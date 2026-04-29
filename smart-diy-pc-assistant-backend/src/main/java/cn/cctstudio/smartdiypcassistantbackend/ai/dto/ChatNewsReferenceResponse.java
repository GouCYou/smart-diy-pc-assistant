package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

public record ChatNewsReferenceResponse(
        Long id,
        String title,
        String summary,
        String tag,
        String coverUrl
) {
}
