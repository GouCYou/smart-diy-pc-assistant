package cn.cctstudio.smartdiypcassistantbackend.feedback.dto;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;

import java.time.LocalDateTime;
import java.util.List;

public record FeedbackResponse(
        Long id,
        AuthorResponse author,
        String content,
        List<String> imageKeys,
        List<String> imageUrls,
        String status,
        String statusLabel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime receivedAt,
        LocalDateTime resolvedAt
) {
}
