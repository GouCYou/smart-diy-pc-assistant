package cn.cctstudio.smartdiypcassistantbackend.file.dto;

public record UploadTokenResponse(String token, String key, String domain, String uploadUrl) {
}
