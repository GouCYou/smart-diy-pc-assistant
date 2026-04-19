package cn.cctstudio.smartdiypcassistantbackend.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 64, message = "长度不能超过 64 位") String nickname,
        @Size(max = 512, message = "长度不能超过 512 位") String avatarUrl,
        @Size(max = 300, message = "长度不能超过 300 位") String bio
) {
}
