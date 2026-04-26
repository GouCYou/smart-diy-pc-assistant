package cn.cctstudio.smartdiypcassistantbackend.auth.dto;

public record CaptchaResponse(
        String captchaId,
        String image
) {
}
