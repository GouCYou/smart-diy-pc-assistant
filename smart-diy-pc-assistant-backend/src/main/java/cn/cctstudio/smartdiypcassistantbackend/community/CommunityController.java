package cn.cctstudio.smartdiypcassistantbackend.community;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityCommentRequest;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityCommentResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostRequest;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/posts")
    public ApiResponse<List<CommunityPostResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(communityService.list(currentUserId(principal)));
    }

    @PostMapping("/posts")
    public ApiResponse<CommunityPostResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CommunityPostRequest request
    ) {
        return ApiResponse.ok(communityService.create(principal.userId(), request));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<CommunityPostResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(communityService.detail(currentUserId(principal), id));
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<CommunityPostResponse> like(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(communityService.toggleLike(principal.userId(), id));
    }

    @PostMapping("/posts/{id}/favorite")
    public ApiResponse<CommunityPostResponse> favorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(communityService.toggleFavorite(principal.userId(), id));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        communityService.deleteOwnPost(principal.userId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<List<CommunityCommentResponse>> comments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(communityService.listComments(currentUserId(principal), id));
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<CommunityCommentResponse> comment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CommunityCommentRequest request
    ) {
        return ApiResponse.ok(communityService.createComment(principal.userId(), id, request));
    }

    @PostMapping("/comments/{id}/like")
    public ApiResponse<CommunityCommentResponse> likeComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(communityService.toggleCommentLike(principal.userId(), id));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        communityService.deleteOwnComment(principal.userId(), id);
        return ApiResponse.ok();
    }

    private Long currentUserId(UserPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
