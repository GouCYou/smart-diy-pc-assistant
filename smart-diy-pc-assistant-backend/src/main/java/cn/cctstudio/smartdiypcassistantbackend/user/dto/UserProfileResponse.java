package cn.cctstudio.smartdiypcassistantbackend.user.dto;

public record UserProfileResponse(
        Long userId,
        String username,
        String email,
        String nickname,
        String avatarUrl,
        String bio,
        String role
) {
}
