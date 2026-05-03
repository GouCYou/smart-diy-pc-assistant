package cn.cctstudio.smartdiypcassistantbackend.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FeedbackRequest(
        @NotBlank(message = "反馈内容不能为空")
        @Size(max = 1200, message = "反馈内容最多 1200 字")
        String content,
        @Size(max = 9, message = "最多上传 9 张图片")
        List<@Size(max = 512, message = "图片地址过长") String> imageKeys
) {
}
