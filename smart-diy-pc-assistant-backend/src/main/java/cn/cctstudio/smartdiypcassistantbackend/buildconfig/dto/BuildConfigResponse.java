package cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfig;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigSource;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record BuildConfigResponse(
        Long id,
        String title,
        String note,
        Integer totalPrice,
        Integer performanceScore,
        String compatibilityStatus,
        List<String> compatibilityMessages,
        List<HardwarePartResponse> parts,
        String summary,
        String content,
        BuildConfigSource source,
        boolean favorite,
        LocalDateTime updatedAt
) {

    public static BuildConfigResponse from(BuildConfig config, List<HardwarePart> parts) {
        return new BuildConfigResponse(
                config.getId(),
                config.getTitle(),
                config.getNote(),
                config.getTotalPrice(),
                config.getPerformanceScore(),
                config.getCompatibilityStatus(),
                splitMessages(config.getCompatibilityMessages()),
                parts.stream().map(HardwarePartResponse::from).toList(),
                config.getSummary(),
                config.getContent(),
                config.getSource(),
                config.isFavorite(),
                config.getUpdatedAt()
        );
    }

    private static List<String> splitMessages(String messages) {
        if (messages == null || messages.isBlank()) {
            return List.of();
        }
        return Arrays.stream(messages.split("\\n"))
                .filter(message -> !message.isBlank())
                .toList();
    }
}
