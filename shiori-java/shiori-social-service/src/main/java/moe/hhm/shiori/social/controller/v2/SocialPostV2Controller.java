package moe.hhm.shiori.social.controller.v2;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import moe.hhm.shiori.social.dto.v2.CreatePostV2Request;
import moe.hhm.shiori.social.dto.v2.PostV2ItemResponse;
import moe.hhm.shiori.social.dto.v2.PostV2PageResponse;
import moe.hhm.shiori.social.security.CurrentUserSupport;
import moe.hhm.shiori.social.service.SocialPostService;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/social")
public class SocialPostV2Controller {

    private final SocialPostService socialPostService;

    public SocialPostV2Controller(SocialPostService socialPostService) {
        this.socialPostService = socialPostService;
    }

    @PostMapping("/posts")
    public PostV2ItemResponse createPost(@Valid @RequestBody CreatePostV2Request request,
                                         Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return socialPostService.createManualPost(userId, request);
    }

    @DeleteMapping("/posts/{postId}")
    public Map<String, Boolean> deletePost(@PathVariable Long postId,
                                           Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        socialPostService.deleteMyPost(postId, userId);
        return Map.of("success", true);
    }

    @GetMapping("/users/{authorUserId}/posts")
    public PostV2PageResponse listByAuthor(@PathVariable Long authorUserId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return socialPostService.listPostsByAuthor(authorUserId, page, size);
    }

    @GetMapping("/square/feed")
    public PostV2PageResponse listSquareFeed(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return socialPostService.listSquareFeed(userId, page, size);
    }

    @GetMapping("/posts")
    public PostV2PageResponse listFeed(@RequestParam(required = false) String authorUserIds,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return socialPostService.listPostsByAuthors(parseAuthorUserIds(authorUserIds), page, size);
    }

    private List<Long> parseAuthorUserIds(String authorUserIds) {
        if (!StringUtils.hasText(authorUserIds)) {
            return List.of();
        }
        String[] tokens = authorUserIds.split(",");
        List<Long> ids = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                long value = Long.parseLong(token.trim());
                if (value > 0) {
                    ids.add(value);
                }
            } catch (NumberFormatException ignored) {
                // ignore invalid id
            }
        }
        return ids.stream().distinct().toList();
    }
}
