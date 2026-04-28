package cn.cctstudio.smartdiypcassistantbackend.notification.dto;

import java.time.LocalDateTime;

public record AdminSystemNotificationResponse(
        String title,
        String content,
        int recipientCount,
        LocalDateTime createdAt
) {
}
