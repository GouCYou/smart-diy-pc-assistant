package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.news.News;
import cn.cctstudio.smartdiypcassistantbackend.news.NewsStatus;

import java.time.LocalDateTime;
import java.util.List;

public record NewsResponse(
        Long id,
        String title,
        String summary,
        String tag,
        String content,
        String coverKey,
        String coverUrl,
        long viewCount,
        long likeCount,
        long commentCount,
        long favoriteCount,
        boolean liked,
        boolean favorite,
        NewsStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<NewsCommentResponse> comments
) {
    public static NewsResponse from(News news, MediaUrlService mediaUrlService) {
        return new NewsResponse(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getTag(),
                news.getContent(),
                news.getCoverKey(),
                mediaUrlService.toPublicUrl(news.getCoverKey()),
                news.getViewCount(),
                0,
                0,
                0,
                false,
                false,
                news.getStatus(),
                news.getCreatedAt(),
                news.getUpdatedAt(),
                List.of()
        );
    }
}
