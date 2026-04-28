package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.ai.dto.AiSettingsRequest;
import cn.cctstudio.smartdiypcassistantbackend.config.properties.AiProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_runtime_setting")
public class AiRuntimeSetting {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(length = 500)
    private String baseUrl;

    @Column(length = 256)
    private String apiKey;

    @Column(length = 120)
    private String modelName;

    private Integer timeoutSeconds;

    private Integer contextMessageLimit;

    private Integer replyMinChars;

    private Integer replyMaxChars;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AiBuildTriggerMode buildTriggerMode;

    @Column(nullable = false)
    private boolean autoBuildEnabled;

    @Column(nullable = false)
    private boolean requirePurchasableProducts;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AiColorMatchMode colorMatchMode;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String extraSystemPrompt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AiRuntimeSetting() {
    }

    public AiRuntimeSetting(AiProperties properties) {
        this.id = SINGLETON_ID;
        this.baseUrl = properties.baseUrl();
        this.apiKey = "";
        this.modelName = properties.modelName();
        this.timeoutSeconds = properties.timeoutSeconds();
        this.contextMessageLimit = 12;
        this.replyMinChars = 180;
        this.replyMaxChars = 420;
        this.buildTriggerMode = AiBuildTriggerMode.SENSITIVE;
        this.autoBuildEnabled = true;
        this.requirePurchasableProducts = true;
        this.colorMatchMode = AiColorMatchMode.PREFER;
        this.extraSystemPrompt = "";
        this.updatedAt = LocalDateTime.now();
    }

    public void update(AiSettingsRequest request) {
        this.baseUrl = clean(request.baseUrl(), 500);
        this.apiKey = clean(request.apiKey(), 256);
        this.modelName = clean(request.modelName(), 120);
        this.timeoutSeconds = request.timeoutSeconds();
        this.contextMessageLimit = request.contextMessageLimit();
        this.replyMinChars = request.replyMinChars();
        this.replyMaxChars = request.replyMaxChars();
        this.buildTriggerMode = request.buildTriggerMode() == null ? AiBuildTriggerMode.BALANCED : request.buildTriggerMode();
        this.autoBuildEnabled = Boolean.TRUE.equals(request.autoBuildEnabled());
        this.requirePurchasableProducts = Boolean.TRUE.equals(request.requirePurchasableProducts());
        this.colorMatchMode = request.colorMatchMode() == null ? AiColorMatchMode.PREFER : request.colorMatchMode();
        this.extraSystemPrompt = clean(request.extraSystemPrompt(), 2000);
        this.updatedAt = LocalDateTime.now();
    }

    private String clean(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    public Long getId() {
        return id;
    }

    public String getBaseUrl() {
        return baseUrl == null ? "" : baseUrl;
    }

    public String getApiKey() {
        return apiKey == null ? "" : apiKey;
    }

    public String getModelName() {
        return modelName == null ? "" : modelName;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds == null ? 30 : timeoutSeconds;
    }

    public Integer getContextMessageLimit() {
        return contextMessageLimit == null ? 12 : contextMessageLimit;
    }

    public Integer getReplyMinChars() {
        return replyMinChars == null ? 180 : replyMinChars;
    }

    public Integer getReplyMaxChars() {
        return replyMaxChars == null ? 420 : replyMaxChars;
    }

    public AiBuildTriggerMode getBuildTriggerMode() {
        return buildTriggerMode == null ? AiBuildTriggerMode.SENSITIVE : buildTriggerMode;
    }

    public boolean isAutoBuildEnabled() {
        return autoBuildEnabled;
    }

    public boolean isRequirePurchasableProducts() {
        return requirePurchasableProducts;
    }

    public AiColorMatchMode getColorMatchMode() {
        return colorMatchMode == null ? AiColorMatchMode.PREFER : colorMatchMode;
    }

    public String getExtraSystemPrompt() {
        return extraSystemPrompt == null ? "" : extraSystemPrompt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
