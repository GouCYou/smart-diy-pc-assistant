package cn.cctstudio.smartdiypcassistantbackend.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CommunityPostRequest(
        @NotBlank(message = "内容不能为空") @Size(max = 2000, message = "内容不能超过 2000 字") String content,
        List<String> imageKeys,
        Long buildConfigId
) {
}
