package cn.cctstudio.smartdiypcassistantbackend.notification.dto;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;
import cn.cctstudio.smartdiypcassistantbackend.notification.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        AuthorResponse actor,
        boolean unread,
        LocalDateTime createdAt
) {
}
