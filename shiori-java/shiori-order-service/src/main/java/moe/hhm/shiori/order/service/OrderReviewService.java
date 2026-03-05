package moe.hhm.shiori.order.service;

import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.domain.OrderReviewVisibilityStatus;
import moe.hhm.shiori.order.domain.OrderReviewerRole;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.dto.v2.AdminOrderReviewPageResponse;
import moe.hhm.shiori.order.dto.v2.AdminOrderReviewVisibilityResponse;
import moe.hhm.shiori.order.dto.v2.OrderReviewContextResponse;
import moe.hhm.shiori.order.dto.v2.OrderReviewItemResponse;
import moe.hhm.shiori.order.dto.v2.OrderReviewUpsertRequest;
import moe.hhm.shiori.order.dto.v2.PraiseWallItemResponse;
import moe.hhm.shiori.order.dto.v2.PraiseWallPageResponse;
import moe.hhm.shiori.order.dto.v2.UserCreditCompositeResponse;
import moe.hhm.shiori.order.dto.v2.UserCreditProfileResponse;
import moe.hhm.shiori.order.dto.v2.UserCreditRoleProfileResponse;
import moe.hhm.shiori.order.dto.v2.UserReviewItemResponse;
import moe.hhm.shiori.order.dto.v2.UserReviewPageResponse;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.model.OrderReviewEntity;
import moe.hhm.shiori.order.model.OrderReviewRecord;
import moe.hhm.shiori.order.model.OrderReviewRoleAggregateRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import moe.hhm.shiori.order.repository.OrderReviewMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class OrderReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderMapper orderMapper;
    private final OrderReviewMapper orderReviewMapper;
    private final OrderProperties orderProperties;
    private final OrderMetrics orderMetrics;

    public OrderReviewService(OrderMapper orderMapper,
                              OrderReviewMapper orderReviewMapper,
                              OrderProperties orderProperties,
                              OrderMetrics orderMetrics) {
        this.orderMapper = orderMapper;
        this.orderReviewMapper = orderReviewMapper;
        this.orderProperties = orderProperties;
        this.orderMetrics = orderMetrics;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReviewItemResponse createReview(Long reviewerUserId, String orderNo, OrderReviewUpsertRequest request) {
        OrderRecord order = requireOrder(orderNo);
        ensureOrderReviewable(order);
        ReviewParty party = resolveReviewParty(order, reviewerUserId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reviewDeadlineAt = resolveReviewDeadline(order);
        if (reviewDeadlineAt == null || now.isAfter(reviewDeadlineAt)) {
            orderMetrics.incOrderReviewSubmit(party.role().name(), "window_expired");
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        OrderReviewRecord existed = orderReviewMapper.findByOrderNoAndReviewer(orderNo, reviewerUserId);
        if (existed != null) {
            orderMetrics.incOrderReviewSubmit(party.role().name(), "duplicate");
            throw new BizException(OrderErrorCode.ORDER_DUPLICATE_REQUEST, HttpStatus.CONFLICT);
        }

        OrderReviewEntity entity = new OrderReviewEntity();
        entity.setOrderNo(orderNo);
        entity.setReviewerUserId(reviewerUserId);
        entity.setReviewedUserId(party.reviewedUserId());
        entity.setReviewerRole(party.role().name());
        entity.setCommunicationStar(request.communicationStar());
        entity.setTimelinessStar(request.timelinessStar());
        entity.setCredibilityStar(request.credibilityStar());
        entity.setOverallStar(calculateOverallStar(party.role(), request));
        entity.setComment(normalizeComment(request.comment()));
        entity.setVisibilityStatus(OrderReviewVisibilityStatus.VISIBLE.name());
        entity.setEditCount(0);
        try {
            orderReviewMapper.insertReview(entity);
        } catch (DuplicateKeyException ex) {
            orderMetrics.incOrderReviewSubmit(party.role().name(), "duplicate");
            throw new BizException(OrderErrorCode.ORDER_DUPLICATE_REQUEST, HttpStatus.CONFLICT);
        }
        orderMetrics.incOrderReviewSubmit(party.role().name(), "success");

        OrderReviewRecord created = requireReviewByOrderAndReviewer(orderNo, reviewerUserId);
        return toItemResponse(created);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReviewItemResponse updateMyReview(Long reviewerUserId, String orderNo, OrderReviewUpsertRequest request) {
        OrderRecord order = requireOrder(orderNo);
        ensureOrderReviewable(order);
        ReviewParty party = resolveReviewParty(order, reviewerUserId);
        OrderReviewRecord existed = requireReviewByOrderAndReviewer(orderNo, reviewerUserId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reviewDeadlineAt = resolveReviewDeadline(order);
        if (reviewDeadlineAt == null || now.isAfter(reviewDeadlineAt)) {
            orderMetrics.incOrderReviewUpdate(party.role().name(), "window_expired");
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        if (existed.editCount() != null && existed.editCount() >= 1) {
            orderMetrics.incOrderReviewUpdate(party.role().name(), "edit_limit");
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        LocalDateTime editableUntil = existed.createdAt() == null
                ? null
                : existed.createdAt().plusHours(Math.max(1, orderProperties.getReview().getEditHours()));
        if (editableUntil == null || now.isAfter(editableUntil)) {
            orderMetrics.incOrderReviewUpdate(party.role().name(), "edit_timeout");
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderReviewMapper.updateReviewContent(
                existed.id(),
                reviewerUserId,
                request.communicationStar(),
                request.timelinessStar(),
                request.credibilityStar(),
                calculateOverallStar(party.role(), request),
                normalizeComment(request.comment()),
                now,
                existed.editCount() == null ? 0 : existed.editCount()
        );
        if (affected == 0) {
            orderMetrics.incOrderReviewUpdate(party.role().name(), "concurrent_conflict");
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        orderMetrics.incOrderReviewUpdate(party.role().name(), "success");

        OrderReviewRecord updated = requireReviewByOrderAndReviewer(orderNo, reviewerUserId);
        return toItemResponse(updated);
    }

    public OrderReviewContextResponse getOrderReviewContext(Long currentUserId, String orderNo) {
        OrderRecord order = requireOrder(orderNo);
        ensureOrderParticipant(order, currentUserId);
        List<OrderReviewRecord> records = orderReviewMapper.listByOrderNo(orderNo);
        OrderReviewRecord myReview = records.stream()
                .filter(item -> Objects.equals(item.reviewerUserId(), currentUserId))
                .findFirst()
                .orElse(null);
        OrderReviewRecord counterpartyReview = records.stream()
                .filter(item -> !Objects.equals(item.reviewerUserId(), currentUserId))
                .findFirst()
                .orElse(null);
        OrderReviewAccessState accessState = resolveReviewAccessState(order, currentUserId, records);
        return new OrderReviewContextResponse(
                orderNo,
                accessState.myReviewSubmitted(),
                accessState.counterpartyReviewSubmitted(),
                accessState.canCreateReview(),
                accessState.canEditReview(),
                accessState.reviewDeadlineAt(),
                myReview == null ? null : toItemResponse(myReview),
                counterpartyReview == null ? null : toItemResponse(counterpartyReview)
        );
    }

    public UserCreditProfileResponse getUserCreditProfile(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        List<OrderReviewRoleAggregateRecord> aggregates = orderReviewMapper.listRoleAggregatesByReviewedUser(userId);

        UserCreditRoleProfileResponse buyerProfile = buildRoleProfile(aggregates, OrderReviewerRole.SELLER);
        UserCreditRoleProfileResponse sellerProfile = buildRoleProfile(aggregates, OrderReviewerRole.BUYER);
        long totalReviewCount = buyerProfile.reviewCount() + sellerProfile.reviewCount();
        BigDecimal compositeAvgStar = BigDecimal.ZERO;
        int compositeScore = 0;
        String creditGrade = "NEW";
        if (totalReviewCount > 0) {
            BigDecimal buyerWeighted = buyerProfile.avgStar().multiply(BigDecimal.valueOf(buyerProfile.reviewCount()));
            BigDecimal sellerWeighted = sellerProfile.avgStar().multiply(BigDecimal.valueOf(sellerProfile.reviewCount()));
            compositeAvgStar = buyerWeighted.add(sellerWeighted)
                    .divide(BigDecimal.valueOf(totalReviewCount), 1, RoundingMode.HALF_UP);
            compositeScore = compositeAvgStar.multiply(BigDecimal.valueOf(20))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
            creditGrade = resolveCreditGrade(compositeScore);
        }

        orderMetrics.incOrderCreditQuery("credit_profile");
        return new UserCreditProfileResponse(
                userId,
                buyerProfile,
                sellerProfile,
                new UserCreditCompositeResponse(totalReviewCount, compositeAvgStar, compositeScore, creditGrade)
        );
    }

    public PraiseWallPageResponse listPraiseWall(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = orderReviewMapper.countPraiseWallByReviewedUser(userId);
        List<PraiseWallItemResponse> items = orderReviewMapper.listPraiseWallByReviewedUser(userId, normalizedSize, offset)
                .stream()
                .map(item -> new PraiseWallItemResponse(
                        item.id(),
                        item.orderNo(),
                        item.reviewerUserId(),
                        item.reviewerRole(),
                        item.communicationStar(),
                        item.timelinessStar(),
                        item.credibilityStar(),
                        item.overallStar(),
                        item.comment(),
                        item.createdAt()
                ))
                .toList();
        orderMetrics.incOrderCreditQuery("praise_wall");
        return new PraiseWallPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public UserReviewPageResponse listUserReviews(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = orderReviewMapper.countVisibleReviewsByReviewedUser(userId);
        List<UserReviewItemResponse> items = orderReviewMapper.listVisibleReviewsByReviewedUser(userId, normalizedSize, offset)
                .stream()
                .map(item -> new UserReviewItemResponse(
                        item.id(),
                        item.orderNo(),
                        item.reviewerUserId(),
                        item.reviewerRole(),
                        item.communicationStar(),
                        item.timelinessStar(),
                        item.credibilityStar(),
                        item.overallStar(),
                        resolvePublicComment(item.overallStar(), item.comment()),
                        item.createdAt()
                ))
                .toList();
        orderMetrics.incOrderCreditQuery("review_list");
        return new UserReviewPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public AdminOrderReviewPageResponse listAdminReviews(Long reviewedUserId,
                                                         Long reviewerUserId,
                                                         String reviewerRole,
                                                         String visibilityStatus,
                                                         BigDecimal minOverallStar,
                                                         BigDecimal maxOverallStar,
                                                         LocalDateTime createdFrom,
                                                         LocalDateTime createdTo,
                                                         int page,
                                                         int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;

        String normalizedRole = normalizeEnumValue(reviewerRole);
        String normalizedVisibility = normalizeEnumValue(visibilityStatus);
        long total = orderReviewMapper.countAdminReviews(
                reviewedUserId, reviewerUserId, normalizedRole, normalizedVisibility, minOverallStar, maxOverallStar, createdFrom, createdTo
        );
        List<OrderReviewItemResponse> items = orderReviewMapper.listAdminReviews(
                        reviewedUserId, reviewerUserId, normalizedRole, normalizedVisibility, minOverallStar, maxOverallStar, createdFrom, createdTo, normalizedSize, offset
                ).stream()
                .map(this::toItemResponse)
                .toList();
        return new AdminOrderReviewPageResponse(total, normalizedPage, normalizedSize, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminOrderReviewVisibilityResponse updateReviewVisibility(Long operatorUserId,
                                                                     Long reviewId,
                                                                     boolean visible,
                                                                     String reason) {
        if (reviewId == null || reviewId <= 0 || operatorUserId == null || operatorUserId <= 0) {
            throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        String nextStatus = visible ? OrderReviewVisibilityStatus.VISIBLE.name() : OrderReviewVisibilityStatus.HIDDEN_BY_ADMIN.name();
        LocalDateTime now = LocalDateTime.now();
        int affected = orderReviewMapper.updateReviewVisibility(
                reviewId,
                nextStatus,
                normalizeComment(reason),
                operatorUserId,
                now
        );
        if (affected == 0) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        orderMetrics.incOrderReviewModeration(visible ? "restore" : "hide");
        return new AdminOrderReviewVisibilityResponse(reviewId, nextStatus);
    }

    public OrderReviewAccessState resolveReviewAccessState(OrderRecord order, Long currentUserId) {
        if (order == null || currentUserId == null || currentUserId <= 0) {
            return OrderReviewAccessState.empty();
        }
        if (OrderStatus.fromCode(order.status()) != OrderStatus.FINISHED) {
            return OrderReviewAccessState.empty();
        }
        if (!Objects.equals(order.buyerUserId(), currentUserId) && !Objects.equals(order.sellerUserId(), currentUserId)) {
            return OrderReviewAccessState.empty();
        }
        List<OrderReviewRecord> records = orderReviewMapper.listByOrderNo(order.orderNo());
        return resolveReviewAccessState(order, currentUserId, records);
    }

    private OrderReviewAccessState resolveReviewAccessState(OrderRecord order,
                                                            Long currentUserId,
                                                            List<OrderReviewRecord> records) {
        if (OrderStatus.fromCode(order.status()) != OrderStatus.FINISHED) {
            return OrderReviewAccessState.empty();
        }
        LocalDateTime reviewDeadlineAt = resolveReviewDeadline(order);
        if (reviewDeadlineAt == null) {
            return OrderReviewAccessState.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        OrderReviewRecord myReview = records.stream()
                .filter(item -> Objects.equals(item.reviewerUserId(), currentUserId))
                .findFirst()
                .orElse(null);
        boolean counterpartyReviewSubmitted = records.stream()
                .anyMatch(item -> !Objects.equals(item.reviewerUserId(), currentUserId));
        boolean myReviewSubmitted = myReview != null;
        boolean canCreate = !myReviewSubmitted && !now.isAfter(reviewDeadlineAt);
        boolean canEdit = false;
        if (myReview != null && (myReview.editCount() == null || myReview.editCount() < 1) && myReview.createdAt() != null) {
            LocalDateTime editableUntil = myReview.createdAt().plusHours(Math.max(1, orderProperties.getReview().getEditHours()));
            canEdit = !now.isAfter(reviewDeadlineAt) && !now.isAfter(editableUntil);
        }
        return new OrderReviewAccessState(
                myReviewSubmitted,
                counterpartyReviewSubmitted,
                canCreate,
                canEdit,
                reviewDeadlineAt
        );
    }

    private UserCreditRoleProfileResponse buildRoleProfile(List<OrderReviewRoleAggregateRecord> aggregates, OrderReviewerRole reviewerRole) {
        OrderReviewRoleAggregateRecord aggregate = aggregates.stream()
                .filter(item -> reviewerRole.name().equals(item.reviewerRole()))
                .findFirst()
                .orElse(null);
        if (aggregate == null || aggregate.reviewCount() == null || aggregate.reviewCount() <= 0) {
            return new UserCreditRoleProfileResponse(
                    reviewerRole == OrderReviewerRole.BUYER ? "seller" : "buyer",
                    0,
                    BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            );
        }
        BigDecimal avgStar = aggregate.avgOverallStar() == null
                ? BigDecimal.ZERO
                : aggregate.avgOverallStar().setScale(1, RoundingMode.HALF_UP);
        BigDecimal positiveRate = BigDecimal.valueOf(aggregate.positiveCount() == null ? 0L : aggregate.positiveCount())
                .divide(BigDecimal.valueOf(aggregate.reviewCount()), 4, RoundingMode.HALF_UP);
        return new UserCreditRoleProfileResponse(
                reviewerRole == OrderReviewerRole.BUYER ? "seller" : "buyer",
                aggregate.reviewCount(),
                avgStar,
                positiveRate
        );
    }

    private String resolveCreditGrade(int score) {
        if (score >= 96) {
            return "S";
        }
        if (score >= 90) {
            return "A";
        }
        if (score >= 80) {
            return "B";
        }
        if (score >= 70) {
            return "C";
        }
        return "D";
    }

    private String normalizeEnumValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private BigDecimal calculateOverallStar(OrderReviewerRole reviewerRole, OrderReviewUpsertRequest request) {
        double communication = request.communicationStar();
        double timeliness = request.timelinessStar();
        double credibility = request.credibilityStar();
        double overall;
        if (reviewerRole == OrderReviewerRole.BUYER) {
            overall = credibility * 0.4D + communication * 0.3D + timeliness * 0.3D;
        } else {
            overall = timeliness * 0.4D + communication * 0.3D + credibility * 0.3D;
        }
        return BigDecimal.valueOf(overall).setScale(1, RoundingMode.HALF_UP);
    }

    private String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String trimmed = comment.trim();
        int maxLength = Math.max(1, orderProperties.getReview().getCommentMaxLength());
        if (trimmed.length() > maxLength) {
            throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private String resolvePublicComment(BigDecimal overallStar, String comment) {
        if (overallStar == null || overallStar.compareTo(BigDecimal.valueOf(4)) < 0) {
            return null;
        }
        return comment;
    }

    private OrderReviewItemResponse toItemResponse(OrderReviewRecord record) {
        return new OrderReviewItemResponse(
                record.id(),
                record.orderNo(),
                record.reviewerUserId(),
                record.reviewedUserId(),
                record.reviewerRole(),
                record.communicationStar(),
                record.timelinessStar(),
                record.credibilityStar(),
                record.overallStar(),
                record.comment(),
                record.visibilityStatus(),
                record.visibilityReason(),
                record.visibilityOperatorUserId(),
                record.visibilityUpdatedAt(),
                record.editCount(),
                record.lastEditedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private OrderReviewRecord requireReviewByOrderAndReviewer(String orderNo, Long reviewerUserId) {
        OrderReviewRecord review = orderReviewMapper.findByOrderNoAndReviewer(orderNo, reviewerUserId);
        if (review == null) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return review;
    }

    private void ensureOrderReviewable(OrderRecord order) {
        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status != OrderStatus.FINISHED) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
    }

    private void ensureOrderParticipant(OrderRecord order, Long userId) {
        if (!Objects.equals(order.buyerUserId(), userId) && !Objects.equals(order.sellerUserId(), userId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private ReviewParty resolveReviewParty(OrderRecord order, Long reviewerUserId) {
        if (Objects.equals(order.buyerUserId(), reviewerUserId)) {
            return new ReviewParty(OrderReviewerRole.BUYER, order.sellerUserId());
        }
        if (Objects.equals(order.sellerUserId(), reviewerUserId)) {
            return new ReviewParty(OrderReviewerRole.SELLER, order.buyerUserId());
        }
        throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
    }

    private LocalDateTime resolveReviewDeadline(OrderRecord order) {
        LocalDateTime finishedAt = order.finishedAt();
        if (finishedAt == null && OrderStatus.fromCode(order.status()) == OrderStatus.FINISHED) {
            finishedAt = order.updatedAt();
        }
        if (finishedAt == null) {
            return null;
        }
        return finishedAt.plusDays(Math.max(1, orderProperties.getReview().getWindowDays()));
    }

    private OrderRecord requireOrder(String orderNo) {
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo);
        if (order == null || isDeleted(order)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return order;
    }

    private boolean isDeleted(OrderRecord order) {
        return order.isDeleted() != null && order.isDeleted() == 1;
    }

    private record ReviewParty(OrderReviewerRole role, Long reviewedUserId) {
    }
}
