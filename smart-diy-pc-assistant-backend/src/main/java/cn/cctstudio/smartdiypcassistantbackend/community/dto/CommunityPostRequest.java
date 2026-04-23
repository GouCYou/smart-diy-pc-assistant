package cn.cctstudio.smartdiypcassistantbackend.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CommunityPostRequest(
        @NotBlank(message = "content cannot be blank") @Size(max = 2000, message = "content too long") String content,
        List<String> imageKeys,
        Long buildConfigId
) {
}
