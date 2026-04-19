package cn.cctstudio.smartdiypcassistantbackend.ai.dto;

import java.time.LocalDateTime;

public record ConversationResponse(Long id, String title, LocalDateTime updatedAt) {
}
