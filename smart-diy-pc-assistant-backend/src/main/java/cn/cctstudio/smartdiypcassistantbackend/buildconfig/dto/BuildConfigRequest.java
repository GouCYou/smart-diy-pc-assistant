package cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BuildConfigRequest(
        @NotBlank(message = "不能为空") @Size(max = 80, message = "长度不能超过 80 位") String title,
        @Size(max = 500, message = "长度不能超过 500 位") String note,
        List<Long> partIds,
        List<BuildPartSelectionRequest> partSelections,
        BuildConfigSource source
) {
}
