package cn.cctstudio.smartdiypcassistantbackend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.verification-code")
public record VerificationCodeProperties(int expireMinutes, int resendSeconds) {
}
