package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import cn.cctstudio.smartdiypcassistantbackend.news.NewsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NewsRequest(
        @NotBlank(message = "不能为空") @Size(max = 120, message = "不能超过120个字符") String title,
        @NotBlank(message = "不能为空") @Size(max = 500, message = "不能超过500个字符") String summary,
        String content,
        String coverUrl,
        @NotNull(message = "不能为空") NewsStatus status
) {
}
