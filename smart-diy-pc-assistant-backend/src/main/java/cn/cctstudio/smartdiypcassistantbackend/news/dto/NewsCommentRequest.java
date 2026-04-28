package cn.cctstudio.smartdiypcassistantbackend.news.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record NewsCommentRequest(
        @Size(max = 1000, message = "内容不能超过 1000 字") String content,
        List<String> imageKeys,
        Long parentCommentId
) {
}
