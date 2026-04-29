package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.util.StringUtils;

public record UserMemoryPatch(
        String budgetPreference,
        String usagePreference,
        String cpuPlatformPreference,
        String gpuPlatformPreference,
        String preferredBrands,
        String rejectedBrands,
        String appearancePreference,
        String noisePreference,
        String upgradePlan
) {

    public boolean hasAnyValue() {
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
}
