package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;

public record ChatResponse(
        Long conversationId,
        String content,
        boolean fromCache,
        boolean directReply,
        String intent,
        String subIntent,
        String safetyLevel,
        String safetyMessage,
        Long generatedBuildConfigId,
        String generatedBuildConfigTitle,
        String generatedBuildConfigSerialNumber,
        BuildConfigResponse generatedBuildConfig
) {
}
