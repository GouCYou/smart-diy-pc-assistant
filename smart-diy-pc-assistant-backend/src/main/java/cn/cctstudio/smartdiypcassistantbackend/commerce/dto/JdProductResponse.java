package cn.cctstudio.smartdiypcassistantbackend.commerce.dto;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;

public record JdProductResponse(
        String title,
        Integer price,
        String image,
        String link,
        String skuId,
        String shopName,
        String matchedModel,
        Long hardwarePartId,
        HardwarePartType type,
        Integer performanceScore,
        String socketType,
        String memoryType,
        String formFactor,
        Integer wattage,
        Integer gpuLengthMm,
        Integer coolerHeightMm,
        String supportedFormFactors,
        String specs
) {
}
