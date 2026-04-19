package cn.cctstudio.smartdiypcassistantbackend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String baseUrl, String apiKey, String modelName, int timeoutSeconds) {
}
