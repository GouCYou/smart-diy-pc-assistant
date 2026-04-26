package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

public record BuildPartSelection(
        Long hardwarePartId,
        String title,
        Integer price,
        String image,
        String link,
        String skuId,
        String matchedModel
) {
}
