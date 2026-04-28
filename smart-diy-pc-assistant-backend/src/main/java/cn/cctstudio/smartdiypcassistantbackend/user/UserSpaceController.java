package cn.cctstudio.smartdiypcassistantbackend.user;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import cn.cctstudio.smartdiypcassistantbackend.user.dto.UserFollowListItemResponse;
import cn.cctstudio.smartdiypcassistantbackend.user.dto.UserSpaceResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserSpaceController {

    private final UserSpaceService userSpaceService;

    public UserSpaceController(UserSpaceService userSpaceService) {
        this.userSpaceService = userSpaceService;
    }

    @GetMapping("/{id}/space")
    public ApiResponse<UserSpaceResponse> space(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(userSpaceService.getSpace(currentUserId(principal), id));
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<UserSpaceResponse> follow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(userSpaceService.toggleFollow(currentUserId(principal), id));
    }

    @GetMapping("/{id}/following")
    public ApiResponse<List<UserFollowListItemResponse>> following(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(userSpaceService.listFollowing(currentUserId(principal), id));
    }

    @GetMapping("/{id}/followers")
    public ApiResponse<List<UserFollowListItemResponse>> followers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(userSpaceService.listFollowers(currentUserId(principal), id));
    }

    private Long currentUserId(UserPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
