package cn.cctstudio.smartdiypcassistantbackend.user.dto;

import java.time.LocalDateTime;

public record UserFollowListItemResponse(
        Long userId,
        String username,
        String nickname,
        String avatarUrl,
        String bio,
        LocalDateTime followedAt,
        boolean following,
        boolean mine
) {
}
