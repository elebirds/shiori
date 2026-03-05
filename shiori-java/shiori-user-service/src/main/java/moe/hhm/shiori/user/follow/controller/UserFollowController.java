package moe.hhm.shiori.user.follow.controller;

import moe.hhm.shiori.user.follow.dto.FollowUserPageResponse;
import moe.hhm.shiori.user.follow.service.UserFollowService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserFollowController {

    private final UserFollowService userFollowService;

    public UserFollowController(UserFollowService userFollowService) {
        this.userFollowService = userFollowService;
    }

    @PostMapping("/follows/{targetUserNo}")
    public Map<String, Boolean> follow(@PathVariable String targetUserNo,
                                       Authentication authentication) {
        Long currentUserId = CurrentUserSupport.requireUserId(authentication);
        userFollowService.followByUserNo(currentUserId, targetUserNo);
        return Map.of("success", true);
    }

    @DeleteMapping("/follows/{targetUserNo}")
    public Map<String, Boolean> unfollow(@PathVariable String targetUserNo,
                                         Authentication authentication) {
        Long currentUserId = CurrentUserSupport.requireUserId(authentication);
        userFollowService.unfollowByUserNo(currentUserId, targetUserNo);
        return Map.of("success", true);
    }

    @GetMapping("/profiles/{userNo}/followers")
    public FollowUserPageResponse listFollowers(@PathVariable String userNo,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return userFollowService.listFollowers(userNo, page, size);
    }

    @GetMapping("/profiles/{userNo}/following")
    public FollowUserPageResponse listFollowing(@PathVariable String userNo,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return userFollowService.listFollowing(userNo, page, size);
    }
}
