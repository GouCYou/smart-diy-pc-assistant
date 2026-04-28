package cn.cctstudio.smartdiypcassistantbackend.community.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;

import java.time.LocalDateTime;
import java.util.List;

public record CommunityCommentResponse(
        Long id,
        Long postId,
        AuthorResponse author,
        Long parentCommentId,
        AuthorResponse replyTo,
        String content,
        List<MentionResponse> mentions,
        List<String> imageUrls,
        BuildConfigResponse buildConfig,
        long likeCount,
        boolean liked,
        LocalDateTime createdAt
) {
}
