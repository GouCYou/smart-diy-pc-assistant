package cn.cctstudio.smartdiypcassistantbackend.hardware.dto;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record HardwarePartRequest(
        @NotNull(message = "不能为空") HardwarePartType type,
        @Size(max = 60, message = "长度不能超过 60 位") String brand,
        @NotBlank(message = "不能为空") @Size(max = 120, message = "长度不能超过 120 位") String model,
        @PositiveOrZero(message = "不能小于 0") Integer price,
        @NotNull(message = "不能为空") @PositiveOrZero(message = "不能小于 0") Integer performanceScore,
        String socketType,
        String memoryType,
        String formFactor,
        Integer wattage,
        Integer gpuLengthMm,
        Integer coolerHeightMm,
        String supportedFormFactors,
        String specs,
        Boolean enabled
) {
}
