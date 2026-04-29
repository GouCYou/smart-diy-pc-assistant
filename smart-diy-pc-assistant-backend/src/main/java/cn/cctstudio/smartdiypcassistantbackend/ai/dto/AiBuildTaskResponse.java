package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

import cn.cctstudio.smartdiypcassistantbackend.ai.AiBuildTask;

import java.time.LocalDateTime;

public record AiBuildTaskResponse(
        Long id,
        Long conversationId,
        String status,
        Integer progress,
        String stageTitle,
        String stageDescription,
        String message,
        Long buildConfigId,
        String buildConfigTitle,
        String buildConfigSerialNumber,
        String errorMessage,
        LocalDateTime updatedAt
) {

    public static AiBuildTaskResponse from(AiBuildTask task) {
        return new AiBuildTaskResponse(
                task.getId(),
                task.getConversationId(),
                task.getStatus().name(),
                task.getProgress(),
                task.getStageTitle(),
                task.getStageDescription(),
                task.getUserFacingMessage(),
                task.getBuildConfigId(),
                task.getBuildConfigTitle(),
                task.getBuildConfigSerialNumber(),
                task.getErrorMessage(),
                task.getUpdatedAt()
        );
    }
}
