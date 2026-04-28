package cn.cctstudio.smartdiypcassistantbackend.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CommunityCommentRequest(
        @NotBlank(message = "内容不能为空") @Size(max = 1000, message = "内容不能超过 1000 字") String content,
        List<String> imageKeys,
        Long parentCommentId,
        Long buildConfigId
) {
}
