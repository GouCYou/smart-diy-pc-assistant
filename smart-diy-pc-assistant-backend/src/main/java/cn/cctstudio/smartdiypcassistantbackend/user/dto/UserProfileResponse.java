package cn.cctstudio.smartdiypcassistantbackend.user.dto;

public record UserProfileResponse(
        Long userId,
        String username,
        String email,
        String nickname,
        String avatarKey,
        String avatarUrl,
        String bio,
        String role
) {
}
