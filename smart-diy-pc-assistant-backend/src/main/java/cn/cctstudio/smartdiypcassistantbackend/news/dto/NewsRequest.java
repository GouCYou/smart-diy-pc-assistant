package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import cn.cctstudio.smartdiypcassistantbackend.news.NewsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NewsRequest(
        @NotBlank(message = "title cannot be blank") @Size(max = 120, message = "title too long") String title,
        @Size(max = 500, message = "summary too long") String summary,
        @Size(max = 30, message = "tag too long") String tag,
        String content,
        String coverKey,
        @NotNull(message = "status is required") NewsStatus status
) {
}
