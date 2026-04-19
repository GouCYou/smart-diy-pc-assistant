package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import cn.cctstudio.smartdiypcassistantbackend.news.News;
import cn.cctstudio.smartdiypcassistantbackend.news.NewsStatus;

import java.time.LocalDateTime;

public record NewsResponse(
        Long id,
        String title,
        String summary,
        String content,
        String coverUrl,
        NewsStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NewsResponse from(News news) {
        return new NewsResponse(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getContent(),
                news.getCoverUrl(),
                news.getStatus(),
                news.getCreatedAt(),
                news.getUpdatedAt()
        );
    }
}
