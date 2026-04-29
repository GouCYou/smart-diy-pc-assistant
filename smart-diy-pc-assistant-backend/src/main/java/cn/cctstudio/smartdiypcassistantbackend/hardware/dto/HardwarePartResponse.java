package cn.cctstudio.smartdiypcassistantbackend.hardware.dto;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;

public record HardwarePartResponse(
        Long id,
        HardwarePartType type,
        String brand,
        String model,
        Integer price,
        String productTitle,
        Integer productPrice,
        String productImage,
        String productLink,
        String skuId,
        String matchedModel,
        Integer performanceScore,
        String socketType,
        String memoryType,
        String formFactor,
        Integer wattage,
        Integer gpuLengthMm,
        Integer coolerHeightMm,
        String supportedFormFactors,
        String specs,
        boolean enabled
) {

    public static HardwarePartResponse from(HardwarePart part) {
        return new HardwarePartResponse(
                part.getId(),
                part.getType(),
                "",
                part.getModel(),
                0,
                "",
                0,
                "",
                "",
                "",
                part.getModel(),
                part.getPerformanceScore(),
                part.getSocketType(),
                part.getMemoryType(),
                part.getFormFactor(),
                part.getWattage(),
                part.getGpuLengthMm(),
                part.getCoolerHeightMm(),
                part.getSupportedFormFactors(),
                part.getSpecs(),
                part.isEnabled()
        );
    }

    public static HardwarePartResponse from(
            HardwarePart part,
            String productTitle,
            Integer productPrice,
            String productImage,
            String productLink,
            String skuId,
            String matchedModel
    ) {
        return new HardwarePartResponse(
                part.getId(),
                part.getType(),
                "",
                part.getModel(),
                0,
                productTitle == null ? "" : productTitle,
                productPrice == null ? 0 : productPrice,
                productImage == null ? "" : productImage,
                productLink == null ? "" : productLink,
                skuId == null ? "" : skuId,
                matchedModel == null || matchedModel.isBlank() ? part.getModel() : matchedModel,
                part.getPerformanceScore(),
                part.getSocketType(),
                part.getMemoryType(),
                part.getFormFactor(),
                part.getWattage(),
                part.getGpuLengthMm(),
                part.getCoolerHeightMm(),
                part.getSupportedFormFactors(),
                part.getSpecs(),
                part.isEnabled()
        );
    }
}
