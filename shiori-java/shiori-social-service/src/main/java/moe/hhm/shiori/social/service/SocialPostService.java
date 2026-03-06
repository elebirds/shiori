package moe.hhm.shiori.social.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.richtext.RichTextPolicies;
import moe.hhm.shiori.common.richtext.RichTextProcessor;
import moe.hhm.shiori.social.client.UserServiceClient;
import moe.hhm.shiori.social.domain.PostSourceType;
import moe.hhm.shiori.social.dto.v2.CreatePostV2Request;
import moe.hhm.shiori.social.dto.v2.PostRelatedProductResponse;
import moe.hhm.shiori.social.dto.v2.PostV2ItemResponse;
import moe.hhm.shiori.social.dto.v2.PostV2PageResponse;
import moe.hhm.shiori.social.event.ProductPublishedPayload;
import moe.hhm.shiori.social.model.EventConsumeLogEntity;
import moe.hhm.shiori.social.model.PostEntity;
import moe.hhm.shiori.social.model.PostRecord;
import moe.hhm.shiori.social.repository.SocialPostMapper;
import moe.hhm.shiori.social.storage.OssObjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Service
public class SocialPostService {

    private static final RichTextProcessor CONTENT_HTML_PROCESSOR =
            new RichTextProcessor(RichTextPolicies.productMediaPolicy());

    private final SocialPostMapper socialPostMapper;
    private final OssObjectService ossObjectService;
    private final UserServiceClient userServiceClient;

    public SocialPostService(SocialPostMapper socialPostMapper,
                             OssObjectService ossObjectService,
                             UserServiceClient userServiceClient) {
        this.socialPostMapper = socialPostMapper;
        this.ossObjectService = ossObjectService;
        this.userServiceClient = userServiceClient;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostV2ItemResponse createManualPost(Long authorUserId, CreatePostV2Request request) {
        String normalized = sanitizeContentHtmlForStore(request.contentHtml());
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ProductErrorCode.INVALID_POST_CONTENT, HttpStatus.BAD_REQUEST);
        }

        PostEntity entity = new PostEntity();
        entity.setPostNo(generatePostNo());
        entity.setAuthorUserId(authorUserId);
        entity.setSourceType(PostSourceType.MANUAL.name());
        entity.setContentHtml(normalized);
        socialPostMapper.insertPost(entity);

        if (entity.getId() == null) {
            throw new IllegalStateException("创建帖子后未返回主键");
        }
        PostRecord created = socialPostMapper.findPostById(entity.getId());
        if (created == null || isDeleted(created)) {
            throw new IllegalStateException("创建帖子后未查到记录");
        }
        return toItemResponse(created);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMyPost(Long postId, Long currentUserId) {
        PostRecord record = requirePost(postId);
        if (!currentUserId.equals(record.authorUserId())) {
            throw new BizException(ProductErrorCode.NO_POST_PERMISSION, HttpStatus.FORBIDDEN);
        }
        if (!PostSourceType.MANUAL.name().equals(record.sourceType())) {
            throw new BizException(ProductErrorCode.NO_POST_PERMISSION, HttpStatus.FORBIDDEN);
        }
        socialPostMapper.softDeleteById(postId);
    }

    public PostV2PageResponse listPostsByAuthor(Long authorUserId, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = socialPostMapper.countPostsByAuthorId(authorUserId);
        List<PostRecord> records = socialPostMapper.listPostsByAuthorId(authorUserId, normalizedSize, offset);
        List<PostV2ItemResponse> items = records == null ? List.of() : records.stream().map(this::toItemResponse).toList();
        return new PostV2PageResponse(total, normalizedPage, normalizedSize, items);
    }

    public PostV2PageResponse listPostsByAuthors(List<Long> authorUserIds, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;

        List<Long> normalizedAuthorIds = authorUserIds == null
                ? List.of()
                : authorUserIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (normalizedAuthorIds.isEmpty()) {
            return new PostV2PageResponse(0, normalizedPage, normalizedSize, List.of());
        }

        long total = socialPostMapper.countPostsByAuthorIds(normalizedAuthorIds);
        List<PostRecord> records = socialPostMapper.listPostsByAuthorIds(normalizedAuthorIds, normalizedSize, offset);
        List<PostV2ItemResponse> items = records == null ? List.of() : records.stream().map(this::toItemResponse).toList();
        return new PostV2PageResponse(total, normalizedPage, normalizedSize, items);
    }

    public PostV2PageResponse listSquareFeed(Long currentUserId, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;

        List<Long> authorUserIds = userServiceClient.listFollowingUserIdsIncludingSelf(currentUserId);
        if (authorUserIds.isEmpty()) {
            return new PostV2PageResponse(0, normalizedPage, normalizedSize, List.of());
        }
        long total = socialPostMapper.countPostsByAuthorIds(authorUserIds);
        List<PostRecord> records = socialPostMapper.listPostsByAuthorIds(authorUserIds, normalizedSize, offset);
        List<PostV2ItemResponse> items = records == null ? List.of() : records.stream().map(this::toItemResponse).toList();
        return new PostV2PageResponse(total, normalizedPage, normalizedSize, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleProductPublished(String eventId, ProductPublishedPayload payload) {
        if (!StringUtils.hasText(eventId) || payload == null || payload.ownerUserId() == null || payload.ownerUserId() <= 0) {
            return;
        }
        EventConsumeLogEntity consumeLog = new EventConsumeLogEntity();
        consumeLog.setEventId(eventId);
        consumeLog.setEventType("PRODUCT_PUBLISHED");
        int affected = socialPostMapper.insertConsumeLog(consumeLog);
        if (affected <= 0) {
            return;
        }

        PostEntity entity = new PostEntity();
        entity.setPostNo(generatePostNo());
        entity.setAuthorUserId(payload.ownerUserId());
        entity.setSourceType(PostSourceType.AUTO_PRODUCT.name());
        entity.setContentHtml(autoPostContent(payload));
        entity.setRelatedProductId(payload.productId());
        entity.setRelatedProductNo(payload.productNo());
        entity.setRelatedProductTitle(truncate(payload.title(), 120));
        entity.setRelatedProductCoverObjectKey(payload.coverObjectKey());
        entity.setRelatedProductMinPriceCent(payload.minPriceCent());
        entity.setRelatedProductMaxPriceCent(payload.maxPriceCent());
        entity.setRelatedProductCampusCode(truncate(payload.campusCode(), 64));
        socialPostMapper.insertPost(entity);
    }

    private String autoPostContent(ProductPublishedPayload payload) {
        String title = StringUtils.hasText(payload.title()) ? payload.title().trim() : "新商品";
        return "<p>上新了：" + HtmlUtils.htmlEscape(title) + "</p>";
    }

    private PostRecord requirePost(Long postId) {
        PostRecord record = socialPostMapper.findPostById(postId);
        if (record == null || isDeleted(record)) {
            throw new BizException(ProductErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private boolean isDeleted(PostRecord record) {
        return record.isDeleted() != null && record.isDeleted() == 1;
    }

    private PostV2ItemResponse toItemResponse(PostRecord record) {
        PostRelatedProductResponse relatedProduct = null;
        if (record.relatedProductId() != null) {
            relatedProduct = new PostRelatedProductResponse(
                    record.relatedProductId(),
                    record.relatedProductNo(),
                    record.relatedProductTitle(),
                    record.relatedProductCoverObjectKey(),
                    resolveCoverImageUrl(record.relatedProductCoverObjectKey()),
                    record.relatedProductMinPriceCent(),
                    record.relatedProductMaxPriceCent(),
                    record.relatedProductCampusCode()
            );
        }

        return new PostV2ItemResponse(
                record.id(),
                record.postNo(),
                record.authorUserId(),
                record.sourceType(),
                renderContentHtmlForResponse(record.contentHtml()),
                relatedProduct,
                record.createdAt()
        );
    }

    private String resolveCoverImageUrl(String coverObjectKey) {
        if (!StringUtils.hasText(coverObjectKey)) {
            return null;
        }
        try {
            return ossObjectService.presignGetUrl(coverObjectKey);
        } catch (BizException ignored) {
            return null;
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > 50) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        return size;
    }

    private String generatePostNo() {
        return "T" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private String sanitizeContentHtmlForStore(String rawContentHtml) {
        return CONTENT_HTML_PROCESSOR.sanitizeForStore(rawContentHtml);
    }

    private String renderContentHtmlForResponse(String storedContentHtml) {
        return CONTENT_HTML_PROCESSOR.renderForResponse(storedContentHtml, ossObjectService::presignGetUrl);
    }

    private String truncate(String raw, int maxLen) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen);
    }
}
