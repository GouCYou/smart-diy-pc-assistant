package cn.cctstudio.smartdiypcassistantbackend.auth.dto;

public record VerificationCodeResponse(int resendSeconds, int expireMinutes) {
}
