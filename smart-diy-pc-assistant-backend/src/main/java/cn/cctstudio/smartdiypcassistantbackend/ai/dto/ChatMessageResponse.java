package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageResponse(
        String role,
        String content,
        Long buildConfigId,
        String buildConfigTitle,
        String buildConfigSerialNumber,
        LocalDateTime createdAt,
        List<ChatNewsReferenceResponse> newsReferences
) {
}
