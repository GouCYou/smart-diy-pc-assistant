package cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfig;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildPartSelection;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigSource;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BuildConfigResponse(
        Long id,
        Long ownerUserId,
        String serialNumber,
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

    public static BuildConfigResponse from(BuildConfig config, List<HardwarePart> parts, List<BuildPartSelection> selections, boolean favorite) {
        Map<Long, BuildPartSelection> selectionMap = selections.stream()
                .filter(item -> item.hardwarePartId() != null)
                .collect(Collectors.toMap(BuildPartSelection::hardwarePartId, item -> item, (left, right) -> left));
        return new BuildConfigResponse(
                config.getId(),
                config.getUserId(),
                config.getSerialNumber(),
                config.getTitle(),
                config.getNote(),
                config.getTotalPrice(),
                config.getPerformanceScore(),
                config.getCompatibilityStatus(),
                splitMessages(config.getCompatibilityMessages()),
                parts.stream().map(part -> toPartResponse(part, selectionMap.get(part.getId()))).toList(),
                config.getSummary(),
                config.getContent(),
                config.getSource(),
                favorite,
                config.getUpdatedAt()
        );
    }

    private static HardwarePartResponse toPartResponse(HardwarePart part, BuildPartSelection selection) {
        if (selection == null) {
            return HardwarePartResponse.from(part);
        }
        return HardwarePartResponse.from(
                part,
                selection.title(),
                selection.price(),
                selection.image(),
                selection.link(),
                selection.skuId(),
                selection.matchedModel()
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
