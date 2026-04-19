package cn.cctstudio.smartdiypcassistantbackend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.qiniu")
public record QiniuProperties(String accessKey, String secretKey, String bucket, String domain, String uploadUrl) {
}
