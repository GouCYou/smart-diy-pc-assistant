package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import cn.cctstudio.smartdiypcassistantbackend.news.NewsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NewsRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 120, message = "标题不能超过 120 字") String title,
        @Size(max = 500, message = "摘要不能超过 500 字") String summary,
        @Size(max = 30, message = "标签不能超过 30 字") String tag,
        String content,
        String coverKey,
        @NotNull(message = "状态不能为空") NewsStatus status
) {
}
