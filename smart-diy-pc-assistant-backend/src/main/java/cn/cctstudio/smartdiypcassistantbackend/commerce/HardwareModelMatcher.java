package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HardwareModelMatcher {

    private static final Pattern GPU_PATTERN = Pattern.compile("\\b(RTX|GTX|RX)\\s*-?\\s*(\\d{3,4})(\\s*(TI|SUPER|XT|XTX))?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOARD_PATTERN = Pattern.compile("\\b([ABHXZ]\\d{3,4}[A-Z]{0,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTEL_CPU_PATTERN = Pattern.compile("\\b(I[3579]|U[579]|ULTRA\\s*[579])[-\\s]?(\\d{4,5}[A-Z]{0,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMD_CPU_PATTERN = Pattern.compile("\\b(\\d{4,5}(X3D2|X3D|XT|X|F|G)?)\\b", Pattern.CASE_INSENSITIVE);

    public Optional<HardwarePart> matchPart(String title, HardwarePartType type, List<HardwarePart> candidates) {
        String normalizedTitle = normalize(title);
        return candidates.stream()
                .filter(part -> type == null || part.getType() == type)
                .sorted(Comparator.comparingInt((HardwarePart part) -> normalize(part.getModel()).length()).reversed())
                .filter(part -> matches(normalizedTitle, part, title))
                .findFirst();
    }

    private boolean matches(String normalizedTitle, HardwarePart part, String title) {
        String model = normalize(part.getModel());
        String modelKey = normalize(modelKey(part));
        if (!model.isBlank() && normalizedTitle.contains(model)) {
            return true;
        }
        if (!modelKey.isBlank() && normalizedTitle.contains(modelKey)) {
            return true;
        }
        String extracted = normalize(extractModel(title, part.getType()));
        return !extracted.isBlank()
                && (model.equals(extracted)
                || modelKey.equals(extracted)
                || model.endsWith(extracted)
                || extracted.endsWith(model)
                || modelKey.endsWith(extracted)
                || extracted.endsWith(modelKey));
    }

    public boolean matchesPartModel(String title, HardwarePart part) {
        List<String> extractedModels = extractModels(title, part.getType()).stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .toList();
        String model = normalize(part.getModel());
        String modelKey = normalize(modelKey(part));
        if (!extractedModels.isEmpty()) {
            return extractedModels.stream().anyMatch(extracted ->
                    extracted.equals(model) || extracted.equals(modelKey) || model.endsWith(extracted) || extracted.endsWith(model)
            );
        }
        String normalizedTitle = normalize(title);
        if (part.getType() == HardwarePartType.GPU) {
            return (!model.isBlank() && normalizedTitle.contains(model))
                    || (!modelKey.isBlank() && normalizedTitle.contains(modelKey));
        }
        return (!model.isBlank() && normalizedTitle.contains(model))
                || (!modelKey.isBlank() && normalizedTitle.contains(modelKey))
                || matchesModelTokens(normalizedTitle, part.getModel());
    }

    private List<String> extractModels(String title, HardwarePartType type) {
        String text = title == null ? "" : title;
        List<String> models = new java.util.ArrayList<>();
        if (type == HardwarePartType.GPU) {
            Matcher matcher = GPU_PATTERN.matcher(text);
            while (matcher.find()) {
                String suffix = matcher.group(3) == null ? "" : matcher.group(3).replaceAll("\\s+", " ").trim();
                models.add((matcher.group(1).toUpperCase(Locale.ROOT) + " " + matcher.group(2) + (suffix.isBlank() ? "" : " " + suffix.toUpperCase(Locale.ROOT))).trim());
            }
            return models;
        }
        if (type == HardwarePartType.MOTHERBOARD) {
            Matcher matcher = BOARD_PATTERN.matcher(text);
            while (matcher.find()) {
                models.add(matcher.group(1).toUpperCase(Locale.ROOT));
            }
            return models;
        }
        if (type == HardwarePartType.CPU) {
            Matcher intel = INTEL_CPU_PATTERN.matcher(text);
            while (intel.find()) {
                models.add((intel.group(1).replaceAll("\\s+", " ").toUpperCase(Locale.ROOT) + "-" + intel.group(2).toUpperCase(Locale.ROOT)).trim());
            }
            Matcher amd = AMD_CPU_PATTERN.matcher(text);
            while (amd.find()) {
                models.add(amd.group(1).toUpperCase(Locale.ROOT));
            }
        }
        return models;
    }

    private String modelKey(HardwarePart part) {
        String extracted = extractModel(part.getModel(), part.getType());
        if (!extracted.isBlank()) {
            return extracted;
        }
        if (part.getType() == HardwarePartType.PSU) {
            Matcher matcher = Pattern.compile("\\b\\d{3,4}\\s*W\\b", Pattern.CASE_INSENSITIVE).matcher(part.getModel());
            if (matcher.find()) {
                return matcher.group();
            }
        }
        if (part.getType() == HardwarePartType.CASE && part.getFormFactor() != null && !part.getFormFactor().isBlank()) {
            return part.getFormFactor();
        }
        if (part.getType() == HardwarePartType.COOLER) {
            Matcher matcher = Pattern.compile("\\b\\d{2,3}\\s*MM\\b", Pattern.CASE_INSENSITIVE).matcher(part.getModel() + " " + part.getSpecs());
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return part.getModel();
    }

    private boolean matchesModelTokens(String normalizedTitle, String model) {
        Matcher matcher = Pattern.compile("[A-Z0-9]+", Pattern.CASE_INSENSITIVE).matcher(model == null ? "" : model);
        int matches = 0;
        int tokens = 0;
        while (matcher.find()) {
            String token = normalize(matcher.group());
            if (token.length() <= 1 || "PSU".equals(token) || "CASE".equals(token) || "COOLER".equals(token)) {
                continue;
            }
            tokens++;
            if (normalizedTitle.contains(token)) {
                matches++;
            }
        }
        if (tokens == 0) {
            return false;
        }
        return matches >= Math.min(2, tokens);
    }

    public String extractModel(String title, HardwarePartType type) {
        String text = title == null ? "" : title;
        if (type == HardwarePartType.GPU) {
            Matcher matcher = GPU_PATTERN.matcher(text);
            if (matcher.find()) {
                String suffix = matcher.group(3) == null ? "" : matcher.group(3).replaceAll("\\s+", " ").trim();
                return (matcher.group(1).toUpperCase(Locale.ROOT) + " " + matcher.group(2) + (suffix.isBlank() ? "" : " " + suffix.toUpperCase(Locale.ROOT))).trim();
            }
        }
        if (type == HardwarePartType.MOTHERBOARD) {
            Matcher matcher = BOARD_PATTERN.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).toUpperCase(Locale.ROOT);
            }
        }
        if (type == HardwarePartType.CPU) {
            Matcher intel = INTEL_CPU_PATTERN.matcher(text);
            if (intel.find()) {
                return (intel.group(1).replaceAll("\\s+", " ").toUpperCase(Locale.ROOT) + "-" + intel.group(2).toUpperCase(Locale.ROOT)).trim();
            }
            Matcher amd = AMD_CPU_PATTERN.matcher(text);
            if (amd.find()) {
                return amd.group(1).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT)
                .replace("锐龙", "RYZEN")
                .replace("酷睿", "CORE")
                .replaceAll("[^A-Z0-9]+", "");
    }
}
