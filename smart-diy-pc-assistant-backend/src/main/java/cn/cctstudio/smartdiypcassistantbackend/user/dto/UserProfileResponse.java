package cn.cctstudio.smartdiypcassistantbackend.user.dto;

public record UserProfileResponse(
        Long userId,
        String username,
        String email,
        String nickname,
        String avatarKey,
        String avatarUrl,
        String backgroundKey,
        String backgroundUrl,
        String bio,
        String gender,
        String birthday,
        String countryOrRegion,
        String province,
        String city,
        Boolean profilePublic,
        Boolean spacePublic,
        String role
) {
}
