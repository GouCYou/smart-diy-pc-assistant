package cn.cctstudio.smartdiypcassistantbackend.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_memory")
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(length = 120)
    private String budgetPreference;

    @Column(length = 240)
    private String usagePreference;

    @Column(length = 160)
    private String cpuPlatformPreference;

    @Column(length = 160)
    private String gpuPlatformPreference;

    @Column(length = 240)
    private String preferredBrands;

    @Column(length = 240)
    private String rejectedBrands;

    @Column(length = 180)
    private String appearancePreference;

    @Column(length = 160)
    private String noisePreference;

    @Column(length = 240)
    private String upgradePlan;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserMemory() {
    }

    public UserMemory(Long userId) {
        this.userId = userId;
        this.budgetPreference = "";
        this.usagePreference = "";
        this.cpuPlatformPreference = "";
        this.gpuPlatformPreference = "";
        this.preferredBrands = "";
        this.rejectedBrands = "";
        this.appearancePreference = "";
        this.noisePreference = "";
        this.upgradePlan = "";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(UserMemoryPatch patch) {
        this.budgetPreference = value(patch.budgetPreference());
        this.usagePreference = value(patch.usagePreference());
        this.cpuPlatformPreference = value(patch.cpuPlatformPreference());
        this.gpuPlatformPreference = value(patch.gpuPlatformPreference());
        this.preferredBrands = value(patch.preferredBrands());
        this.rejectedBrands = value(patch.rejectedBrands());
        this.appearancePreference = value(patch.appearancePreference());
        this.noisePreference = value(patch.noisePreference());
        this.upgradePlan = value(patch.upgradePlan());
        touch();
    }

    public void merge(UserMemoryPatch patch) {
        if (StringUtils.hasText(patch.budgetPreference())) {
            this.budgetPreference = value(patch.budgetPreference());
        }
        if (StringUtils.hasText(patch.usagePreference())) {
            this.usagePreference = value(patch.usagePreference());
        }
        if (StringUtils.hasText(patch.cpuPlatformPreference())) {
            this.cpuPlatformPreference = value(patch.cpuPlatformPreference());
        }
        if (StringUtils.hasText(patch.gpuPlatformPreference())) {
            this.gpuPlatformPreference = value(patch.gpuPlatformPreference());
        }
        if (StringUtils.hasText(patch.preferredBrands())) {
            this.preferredBrands = value(patch.preferredBrands());
        }
        if (StringUtils.hasText(patch.rejectedBrands())) {
            this.rejectedBrands = value(patch.rejectedBrands());
        }
        if (StringUtils.hasText(patch.appearancePreference())) {
            this.appearancePreference = value(patch.appearancePreference());
        }
        if (StringUtils.hasText(patch.noisePreference())) {
            this.noisePreference = value(patch.noisePreference());
        }
        if (StringUtils.hasText(patch.upgradePlan())) {
            this.upgradePlan = value(patch.upgradePlan());
        }
        touch();
    }

    public boolean hasAnyMemory() {
        return StringUtils.hasText(budgetPreference)
                || StringUtils.hasText(usagePreference)
                || StringUtils.hasText(cpuPlatformPreference)
                || StringUtils.hasText(gpuPlatformPreference)
                || StringUtils.hasText(preferredBrands)
                || StringUtils.hasText(rejectedBrands)
                || StringUtils.hasText(appearancePreference)
                || StringUtils.hasText(noisePreference)
                || StringUtils.hasText(upgradePlan);
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getBudgetPreference() {
        return budgetPreference == null ? "" : budgetPreference;
    }

    public String getUsagePreference() {
        return usagePreference == null ? "" : usagePreference;
    }

    public String getCpuPlatformPreference() {
        return cpuPlatformPreference == null ? "" : cpuPlatformPreference;
    }

    public String getGpuPlatformPreference() {
        return gpuPlatformPreference == null ? "" : gpuPlatformPreference;
    }

    public String getPreferredBrands() {
        return preferredBrands == null ? "" : preferredBrands;
    }

    public String getRejectedBrands() {
        return rejectedBrands == null ? "" : rejectedBrands;
    }

    public String getAppearancePreference() {
        return appearancePreference == null ? "" : appearancePreference;
    }

    public String getNoisePreference() {
        return noisePreference == null ? "" : noisePreference;
    }

    public String getUpgradePlan() {
        return upgradePlan == null ? "" : upgradePlan;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
