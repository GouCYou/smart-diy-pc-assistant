package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

import cn.cctstudio.smartdiypcassistantbackend.ai.AiBuildTriggerMode;
import cn.cctstudio.smartdiypcassistantbackend.ai.AiColorMatchMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSettingsRequest(
        @NotBlank
        @Size(max = 500)
        String baseUrl,

        @Size(max = 256)
        String apiKey,

        @NotBlank
        @Size(max = 120)
        String modelName,

        @Min(5)
        @Max(180)
        Integer timeoutSeconds,

        @Min(4)
        @Max(30)
        Integer contextMessageLimit,

        @Min(60)
        @Max(800)
        Integer replyMinChars,

        @Min(120)
        @Max(1200)
        Integer replyMaxChars,

        AiBuildTriggerMode buildTriggerMode,

        Boolean autoBuildEnabled,

        Boolean requirePurchasableProducts,

        AiColorMatchMode colorMatchMode,

        @Size(max = 2000)
        String extraSystemPrompt
) {
}
