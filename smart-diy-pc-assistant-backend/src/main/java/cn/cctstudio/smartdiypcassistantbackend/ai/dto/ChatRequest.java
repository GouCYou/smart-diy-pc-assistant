package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        Long conversationId,
        Long buildConfigId,
        @NotBlank(message = "不能为空")
        @Size(max = 4000, message = "不能超过4000个字符")
        String message
) {
}
