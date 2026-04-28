package cn.cctstudio.smartdiypcassistantbackend.user.dto;

public record UserMentionSearchResponse(
        Long userId,
        String nickname,
        String avatarUrl
) {
}
