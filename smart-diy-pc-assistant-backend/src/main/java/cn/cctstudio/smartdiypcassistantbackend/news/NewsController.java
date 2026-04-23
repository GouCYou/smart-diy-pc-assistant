package cn.cctstudio.smartdiypcassistantbackend.news;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsCommentRequest;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsCommentResponse;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public ApiResponse<List<NewsResponse>> listPublished(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "latest") String sort
    ) {
        return ApiResponse.ok(newsService.listPublished(currentUserId(principal), sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<NewsResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(newsService.detail(currentUserId(principal), id));
    }

    @PostMapping("/{id}/like")
    public ApiResponse<NewsResponse> like(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(newsService.toggleLike(principal.userId(), id));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<NewsResponse> favorite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(newsService.toggleFavorite(principal.userId(), id));
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<List<NewsCommentResponse>> comments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam(defaultValue = "latest") String sort
    ) {
        return ApiResponse.ok(newsService.listComments(currentUserId(principal), id, sort));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<NewsCommentResponse> comment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody NewsCommentRequest request
    ) {
        return ApiResponse.ok(newsService.createComment(principal.userId(), id, request));
    }

    @PostMapping("/comments/{id}/like")
    public ApiResponse<NewsCommentResponse> likeComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(newsService.toggleCommentLike(principal.userId(), id));
    }

    private Long currentUserId(UserPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
