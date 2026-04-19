package cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BuildConfigRequest(
        @NotBlank(message = "不能为空") @Size(max = 80, message = "长度不能超过 80 位") String title,
        @Size(max = 500, message = "长度不能超过 500 位") String note,
        @NotEmpty(message = "请至少选择一个硬件") List<Long> partIds,
        BuildConfigSource source
) {
}
