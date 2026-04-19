package cn.cctstudio.smartdiypcassistantbackend.file.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadTokenRequest(@NotBlank(message = "不能为空") String filename) {
}
