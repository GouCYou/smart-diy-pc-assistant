package cn.cctstudio.smartdiypcassistantbackend.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 64, message = "nickname too long") String nickname,
        @Size(max = 512, message = "avatar key too long") String avatarKey,
        @Size(max = 300, message = "bio too long") String bio
) {
}
