package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record NewsCommentRequest(
        @Size(max = 1000, message = "content too long") String content,
        List<String> imageKeys
) {
}
