package cn.cctstudio.smartdiypcassistantbackend.hardware.dto;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;

public record HardwarePartResponse(
        Long id,
        HardwarePartType type,
        String brand,
        String model,
        Integer price,
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
                part.getBrand(),
                part.getModel(),
                part.getPrice(),
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
