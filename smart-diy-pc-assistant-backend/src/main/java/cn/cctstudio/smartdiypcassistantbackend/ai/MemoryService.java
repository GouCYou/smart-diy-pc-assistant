package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class MemoryService {

    private final UserMemoryRepository repository;

    public MemoryService(UserMemoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<UserMemory> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public String promptContext(Long userId) {
        return repository.findByUserId(userId)
                .filter(UserMemory::hasAnyMemory)
                .map(this::formatPromptContext)
                .orElse("无");
    }

    @Transactional
    public UserMemory update(Long userId, UserMemoryPatch patch) {
        UserMemory memory = repository.findByUserId(userId).orElseGet(() -> new UserMemory(userId));
        memory.update(normalize(patch));
        return repository.save(memory);
    }

    @Transactional
    public UserMemory merge(Long userId, UserMemoryPatch patch) {
        UserMemoryPatch cleanPatch = normalize(patch);
        UserMemory memory = repository.findByUserId(userId).orElseGet(() -> new UserMemory(userId));
        if (!cleanPatch.hasAnyValue()) {
            return memory;
        }
        String preferredBrands = mergeTokenList(memory.getPreferredBrands(), cleanPatch.preferredBrands());
        String rejectedBrands = mergeTokenList(memory.getRejectedBrands(), cleanPatch.rejectedBrands());
        preferredBrands = removeTokens(preferredBrands, cleanPatch.rejectedBrands());
        rejectedBrands = removeTokens(rejectedBrands, cleanPatch.preferredBrands());
        memory.merge(new UserMemoryPatch(
                cleanPatch.budgetPreference(),
                cleanPatch.usagePreference(),
                cleanPatch.cpuPlatformPreference(),
                cleanPatch.gpuPlatformPreference(),
                preferredBrands,
                rejectedBrands,
                cleanPatch.appearancePreference(),
                cleanPatch.noisePreference(),
                cleanPatch.upgradePlan()
        ));
        return repository.save(memory);
    }

    public String formatPromptContext(UserMemory memory) {
        if (memory == null || !memory.hasAnyMemory()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "预算偏好", memory.getBudgetPreference());
        appendLine(builder, "用途偏好", memory.getUsagePreference());
        appendLine(builder, "CPU 平台偏好", memory.getCpuPlatformPreference());
        appendLine(builder, "GPU 平台偏好", memory.getGpuPlatformPreference());
        appendLine(builder, "品牌喜好", memory.getPreferredBrands());
        appendLine(builder, "品牌排斥", memory.getRejectedBrands());
        appendLine(builder, "外观偏好", memory.getAppearancePreference());
        appendLine(builder, "噪音偏好", memory.getNoisePreference());
        appendLine(builder, "升级计划", memory.getUpgradePlan());
        return builder.isEmpty() ? "无" : builder.toString().trim();
    }

    private UserMemoryPatch normalize(UserMemoryPatch patch) {
        if (patch == null) {
            return new UserMemoryPatch("", "", "", "", "", "", "", "", "");
        }
        return new UserMemoryPatch(
                clean(patch.budgetPreference(), 120),
                clean(patch.usagePreference(), 240),
                clean(patch.cpuPlatformPreference(), 160),
                clean(patch.gpuPlatformPreference(), 160),
                cleanTokenList(patch.preferredBrands(), 240),
                cleanTokenList(patch.rejectedBrands(), 240),
                clean(patch.appearancePreference(), 180),
                clean(patch.noisePreference(), 160),
                clean(patch.upgradePlan(), 240)
        );
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append("：").append(value.trim()).append("\n");
        }
    }

    private String clean(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        text = text.replaceAll("https?://\\S+", "")
                .replaceAll("(?i)sku\\s*[:：]?\\s*[A-Za-z0-9_-]+", "")
                .replaceAll("京东链接\\s*[:：]?\\s*\\S+", "")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String cleanTokenList(String value, int maxLength) {
        Set<String> tokens = tokens(value);
        String result = String.join("、", tokens);
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    private String mergeTokenList(String existing, String incoming) {
        Set<String> merged = tokens(existing);
        merged.addAll(tokens(incoming));
        return String.join("、", merged);
    }

    private String removeTokens(String source, String removed) {
        Set<String> result = tokens(source);
        result.removeAll(tokens(removed));
        return String.join("、", result);
    }

    private Set<String> tokens(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!StringUtils.hasText(value)) {
            return result;
        }
        String[] items = value.trim().split("[,，、\\s]+");
        for (String item : items) {
            String clean = item.trim();
            if (StringUtils.hasText(clean)) {
                result.add(clean);
            }
        }
        return result;
    }
}
