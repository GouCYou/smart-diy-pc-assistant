package cn.cctstudio.smartdiypcassistantbackend.admin.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String nickname,
        String avatarUrl,
        String bio,
        String role,
        boolean enabled,
        LocalDateTime banExpiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
