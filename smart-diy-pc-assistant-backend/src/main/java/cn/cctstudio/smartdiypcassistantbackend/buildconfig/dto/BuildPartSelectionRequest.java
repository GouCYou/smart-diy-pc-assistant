package cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto;

public record BuildPartSelectionRequest(
        Long hardwarePartId,
        String title,
        Integer price,
        String image,
        String link,
        String skuId,
        String matchedModel
) {
}
