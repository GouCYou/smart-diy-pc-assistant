package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;

import java.time.LocalDateTime;
import java.util.List;

public record NewsCommentResponse(
        Long id,
        Long newsId,
        AuthorResponse author,
        Long parentCommentId,
        AuthorResponse replyTo,
        String content,
        List<String> imageUrls,
        long likeCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
