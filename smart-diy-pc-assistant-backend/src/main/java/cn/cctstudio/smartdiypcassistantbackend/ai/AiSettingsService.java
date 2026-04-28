package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.ai.dto.AiSettingsRequest;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.AiSettingsResponse;
import cn.cctstudio.smartdiypcassistantbackend.config.properties.AiProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AiSettingsService {

    private final AiRuntimeSettingRepository repository;
    private final AiProperties properties;

    public AiSettingsService(AiRuntimeSettingRepository repository, AiProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse get() {
        return toResponse(repository.findById(AiRuntimeSetting.SINGLETON_ID)
                .orElseGet(() -> new AiRuntimeSetting(properties)));
    }

    @Transactional
    public AiSettingsResponse update(AiSettingsRequest request) {
        AiRuntimeSetting setting = repository.findById(AiRuntimeSetting.SINGLETON_ID)
                .orElseGet(() -> new AiRuntimeSetting(properties));
        setting.update(normalize(request));
        return toResponse(repository.save(setting));
    }

    @Transactional(readOnly = true)
    public EffectiveAiSettings effective() {
        AiRuntimeSetting setting = repository.findById(AiRuntimeSetting.SINGLETON_ID)
                .orElseGet(() -> new AiRuntimeSetting(properties));
        String apiKey = StringUtils.hasText(setting.getApiKey()) ? setting.getApiKey() : properties.apiKey();
        String baseUrl = StringUtils.hasText(setting.getBaseUrl()) ? setting.getBaseUrl() : properties.baseUrl();
        String modelName = StringUtils.hasText(setting.getModelName()) ? setting.getModelName() : properties.modelName();
        int minChars = clamp(setting.getReplyMinChars(), 60, 800);
        int maxChars = Math.max(minChars, clamp(setting.getReplyMaxChars(), 120, 1200));
        return new EffectiveAiSettings(
                baseUrl,
                apiKey,
                modelName,
                clamp(setting.getTimeoutSeconds(), 5, 180),
                clamp(setting.getContextMessageLimit(), 4, 30),
                minChars,
                maxChars,
                setting.getBuildTriggerMode(),
                setting.isAutoBuildEnabled(),
                setting.isRequirePurchasableProducts(),
                setting.getColorMatchMode(),
                setting.getExtraSystemPrompt()
        );
    }

    private AiSettingsRequest normalize(AiSettingsRequest request) {
        int minChars = clamp(request.replyMinChars(), 60, 800);
        int maxChars = Math.max(minChars, clamp(request.replyMaxChars(), 120, 1200));
        return new AiSettingsRequest(
                fallback(request.baseUrl(), properties.baseUrl()),
                request.apiKey(),
                fallback(request.modelName(), properties.modelName()),
                clamp(request.timeoutSeconds(), 5, 180),
                clamp(request.contextMessageLimit(), 4, 30),
                minChars,
                maxChars,
                request.buildTriggerMode() == null ? AiBuildTriggerMode.SENSITIVE : request.buildTriggerMode(),
                request.autoBuildEnabled() == null || request.autoBuildEnabled(),
                request.requirePurchasableProducts() == null || request.requirePurchasableProducts(),
                request.colorMatchMode() == null ? AiColorMatchMode.PREFER : request.colorMatchMode(),
                request.extraSystemPrompt()
        );
    }

    private AiSettingsResponse toResponse(AiRuntimeSetting setting) {
        boolean hasPropertyKey = StringUtils.hasText(properties.apiKey());
        return new AiSettingsResponse(
                setting.getBaseUrl(),
                setting.getApiKey(),
                StringUtils.hasText(setting.getApiKey()) || hasPropertyKey,
                setting.getModelName(),
                setting.getTimeoutSeconds(),
                setting.getContextMessageLimit(),
                setting.getReplyMinChars(),
                setting.getReplyMaxChars(),
                setting.getBuildTriggerMode(),
                setting.isAutoBuildEnabled(),
                setting.isRequirePurchasableProducts(),
                setting.getColorMatchMode(),
                setting.getExtraSystemPrompt(),
                setting.getUpdatedAt() == null ? LocalDateTime.now() : setting.getUpdatedAt()
        );
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private int clamp(Integer value, int min, int max) {
        int clean = value == null ? min : value;
        return Math.max(min, Math.min(max, clean));
    }

    public record EffectiveAiSettings(
            String baseUrl,
            String apiKey,
            String modelName,
            int timeoutSeconds,
            int contextMessageLimit,
            int replyMinChars,
            int replyMaxChars,
            AiBuildTriggerMode buildTriggerMode,
            boolean autoBuildEnabled,
            boolean requirePurchasableProducts,
            AiColorMatchMode colorMatchMode,
            String extraSystemPrompt
    ) {

        public String cacheSignature() {
            return modelName + ":" + contextMessageLimit + ":" + replyMinChars + ":" + replyMaxChars
                    + ":" + buildTriggerMode + ":" + autoBuildEnabled + ":" + colorMatchMode
                    + ":" + (extraSystemPrompt == null ? "" : extraSystemPrompt.hashCode());
        }
    }
}
