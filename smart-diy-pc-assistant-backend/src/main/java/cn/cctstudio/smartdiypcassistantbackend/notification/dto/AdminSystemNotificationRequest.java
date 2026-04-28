package cn.cctstudio.smartdiypcassistantbackend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSystemNotificationRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 80, message = "标题不能超过 80 字") String title,
        @NotBlank(message = "内容不能为空") @Size(max = 5000, message = "内容不能超过 5000 字") String content
) {
}
