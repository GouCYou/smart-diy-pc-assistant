package cn.cctstudio.smartdiypcassistantbackend.auth.dto;

public record CurrentUserResponse(
        Long userId,
        String username,
        String email,
        String nickname,
        String avatarUrl,
        String bio,
        String role
) {
}
