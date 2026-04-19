package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.news.NewsService;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsRequest;
import cn.cctstudio.smartdiypcassistantbackend.news.dto.NewsResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/news")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNewsController {

    private final NewsService newsService;

    public AdminNewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public ApiResponse<List<NewsResponse>> listAll() {
        return ApiResponse.ok(newsService.listAll());
    }

    @PostMapping
    public ApiResponse<NewsResponse> create(@Valid @RequestBody NewsRequest request) {
        return ApiResponse.ok(newsService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<NewsResponse> update(@PathVariable Long id, @Valid @RequestBody NewsRequest request) {
        return ApiResponse.ok(newsService.update(id, request));
    }
}
