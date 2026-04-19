package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

public record ChatResponse(
        Long conversationId,
        String content,
        boolean fromCache,
        boolean directReply,
        String intent,
        String subIntent,
        String safetyLevel,
        String safetyMessage
) {
}
