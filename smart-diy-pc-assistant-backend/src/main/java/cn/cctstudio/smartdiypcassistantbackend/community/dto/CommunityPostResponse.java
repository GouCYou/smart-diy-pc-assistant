package cn.cctstudio.smartdiypcassistantbackend.community.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;

import java.time.LocalDateTime;
import java.util.List;

public record CommunityPostResponse(
        Long id,
        AuthorResponse author,
        String content,
        List<MentionResponse> mentions,
        List<String> imageUrls,
        BuildConfigResponse buildConfig,
        long likeCount,
        long commentCount,
        long favoriteCount,
        boolean liked,
        boolean favorite,
        LocalDateTime createdAt,
        List<CommunityCommentResponse> comments
) {
}
