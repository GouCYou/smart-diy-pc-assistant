package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

import cn.cctstudio.smartdiypcassistantbackend.ai.AiBuildTriggerMode;
import cn.cctstudio.smartdiypcassistantbackend.ai.AiColorMatchMode;

import java.time.LocalDateTime;

public record AiSettingsResponse(
        String baseUrl,
        String apiKey,
        boolean apiKeyConfigured,
        String modelName,
        Integer timeoutSeconds,
        Integer contextMessageLimit,
        Integer replyMinChars,
        Integer replyMaxChars,
        AiBuildTriggerMode buildTriggerMode,
        boolean autoBuildEnabled,
        boolean requirePurchasableProducts,
        AiColorMatchMode colorMatchMode,
        String extraSystemPrompt,
        LocalDateTime updatedAt
) {
}
