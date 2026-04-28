package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;
import cn.cctstudio.smartdiypcassistantbackend.community.dto.MentionResponse;

import java.time.LocalDateTime;
import java.util.List;

public record NewsCommentResponse(
        Long id,
        Long newsId,
        AuthorResponse author,
        Long parentCommentId,
        AuthorResponse replyTo,
        String content,
        List<MentionResponse> mentions,
        List<String> imageUrls,
        long likeCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
