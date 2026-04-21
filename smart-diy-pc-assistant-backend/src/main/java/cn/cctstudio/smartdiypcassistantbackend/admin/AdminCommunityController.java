package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.CommunityService;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityCommentResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.CommunityPostResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/community")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommunityController {

    private final CommunityService communityService;

    public AdminCommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/posts")
    public ApiResponse<List<CommunityPostResponse>> posts() {
        return ApiResponse.ok(communityService.listAdmin());
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable Long id) {
        communityService.deletePost(id);
        return ApiResponse.ok();
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<List<CommunityCommentResponse>> comments(@PathVariable Long id) {
        return ApiResponse.ok(communityService.listAdminComments(id));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        communityService.deleteComment(id);
        return ApiResponse.ok();
    }
}
