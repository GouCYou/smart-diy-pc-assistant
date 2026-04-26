package cn.cctstudio.smartdiypcassistantbackend.admin.dto;

import java.time.LocalDateTime;

public record AdminUserEnabledRequest(boolean enabled, LocalDateTime banExpiresAt) {
}
