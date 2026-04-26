package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigRequest;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import cn.cctstudio.smartdiypcassistantbackend.user.UserAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BuildConfigService {

    private final BuildConfigRepository buildConfigRepository;
    private final HardwarePartRepository hardwarePartRepository;
    private final UserAccessService userAccessService;

    public BuildConfigService(
            BuildConfigRepository buildConfigRepository,
            HardwarePartRepository hardwarePartRepository,
            UserAccessService userAccessService
    ) {
        this.buildConfigRepository = buildConfigRepository;
        this.hardwarePartRepository = hardwarePartRepository;
        this.userAccessService = userAccessService;
    }

    @Transactional(readOnly = true)
    public List<BuildConfigResponse> list(Long userId, String segment) {
        List<BuildConfig> configs = switch (segment == null ? "mine" : segment) {
            case "all" -> buildConfigRepository.findByUserIdOrderByUpdatedAtDesc(userId);
            case "ai" -> buildConfigRepository.findByUserIdAndSourceOrderByUpdatedAtDesc(userId, BuildConfigSource.AI);
            case "favorite" -> buildConfigRepository.findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(userId);
            default -> buildConfigRepository.findByUserIdAndSourceOrderByUpdatedAtDesc(userId, BuildConfigSource.MANUAL);
        };
        return configs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BuildConfigResponse> listFavoriteByUser(Long userId) {
        return buildConfigRepository.findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BuildConfigResponse get(Long userId, Long id) {
        return toResponse(findOwned(id, userId));
    }

    @Transactional(readOnly = true)
    public BuildConfigResponse getPublic(Long id) {
        return buildConfigRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public BuildConfigResponse create(Long userId, BuildConfigRequest request) {
        userAccessService.requireAvailable(userId);
        BuildConfig config = new BuildConfig(userId, "", "", "", 0, 0, "WARN", "", "", "", BuildConfigSource.MANUAL);
        applyConfig(config, request);
        return toResponse(buildConfigRepository.save(config));
    }

    @Transactional
    public BuildConfigResponse update(Long userId, Long id, BuildConfigRequest request) {
        userAccessService.requireAvailable(userId);
        BuildConfig config = findOwned(id, userId);
        applyConfig(config, request);
        return toResponse(config);
    }

    private void applyConfig(BuildConfig config, BuildConfigRequest request) {
        String title = clean(request.title(), 80, "未命名配置");
        String note = clean(request.note(), 500, "");
        List<HardwarePart> parts = resolveParts(request.partIds());
        int totalPrice = parts.stream().mapToInt(HardwarePart::getPrice).sum();
        int performanceScore = calculatePerformanceScore(parts);
        CompatibilityResult compatibility = checkCompatibility(parts);
        if ("BLOCK".equals(compatibility.status())) {
            throw new BusinessException("存在兼容性问题，请调整红色标记的硬件后再保存");
        }
        String summary = "¥" + totalPrice + " · 性能 " + performanceScore + " 分 · " + statusLabel(compatibility.status());
        String content = createContent(parts, note, totalPrice, performanceScore, compatibility);
        BuildConfigSource source = request.source() == null ? BuildConfigSource.MANUAL : request.source();
        config.updateConfig(
                title,
                note,
                serializePartIds(parts),
                totalPrice,
                performanceScore,
                compatibility.status(),
                String.join("\n", compatibility.messages()),
                clean(summary, 240, "配置单"),
                content,
                source
        );
    }

    @Transactional
    public BuildConfigResponse updateFavorite(Long userId, Long id, boolean favorite) {
        BuildConfig config = findOwned(id, userId);
        config.updateFavorite(favorite);
        return toResponse(config);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        BuildConfig config = findOwned(id, userId);
        buildConfigRepository.delete(config);
    }

    private BuildConfigResponse toResponse(BuildConfig config) {
        return BuildConfigResponse.from(config, resolveParts(parsePartIds(config.getPartIds()), false));
    }

    private BuildConfig findOwned(Long id, Long userId) {
        return buildConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("配置不存在"));
    }

    private List<HardwarePart> resolveParts(Collection<Long> partIds) {
        return resolveParts(partIds, true);
    }

    private List<HardwarePart> resolveParts(Collection<Long> partIds, boolean strict) {
        if (partIds == null || partIds.isEmpty()) {
            if (strict) {
                throw new BusinessException("请至少选择一个硬件");
            }
            return List.of();
        }
        List<Long> cleanIds = partIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, HardwarePart> partMap = hardwarePartRepository.findByIdIn(cleanIds).stream()
                .collect(Collectors.toMap(HardwarePart::getId, part -> part));
        if (strict && partMap.size() != cleanIds.size()) {
            throw new BusinessException("选择的硬件不存在");
        }
        return cleanIds.stream()
                .map(partMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(HardwarePart::getType))
                .toList();
    }

    private List<Long> parsePartIds(String partIds) {
        if (partIds == null || partIds.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String item : partIds.split(",")) {
            try {
                ids.add(Long.parseLong(item.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private String serializePartIds(List<HardwarePart> parts) {
        return parts.stream()
                .map(part -> String.valueOf(part.getId()))
                .collect(Collectors.joining(","));
    }

    private int calculatePerformanceScore(List<HardwarePart> parts) {
        Map<HardwarePartType, HardwarePart> typed = firstByType(parts);
        int weighted = 0;
        int weight = 0;
        weighted += score(typed, HardwarePartType.CPU, 30);
        weight += typed.containsKey(HardwarePartType.CPU) ? 30 : 0;
        weighted += score(typed, HardwarePartType.GPU, 40);
        weight += typed.containsKey(HardwarePartType.GPU) ? 40 : 0;
        weighted += score(typed, HardwarePartType.MEMORY, 12);
        weight += typed.containsKey(HardwarePartType.MEMORY) ? 12 : 0;
        weighted += score(typed, HardwarePartType.STORAGE, 10);
        weight += typed.containsKey(HardwarePartType.STORAGE) ? 10 : 0;
        weighted += score(typed, HardwarePartType.MOTHERBOARD, 8);
        weight += typed.containsKey(HardwarePartType.MOTHERBOARD) ? 8 : 0;
        if (weight == 0) {
            return 0;
        }
        return Math.round((float) weighted / weight);
    }

    private int score(Map<HardwarePartType, HardwarePart> typed, HardwarePartType type, int weight) {
        HardwarePart part = typed.get(type);
        return part == null ? 0 : part.getPerformanceScore() * weight;
    }

    private CompatibilityResult checkCompatibility(List<HardwarePart> parts) {
        Map<HardwarePartType, HardwarePart> typed = firstByType(parts);
        List<String> messages = new ArrayList<>();
        boolean blocked = false;

        Optional.ofNullable(typed.get(HardwarePartType.CPU)).ifPresentOrElse(cpu -> {
        }, () -> messages.add("建议补充 CPU。"));
        Optional.ofNullable(typed.get(HardwarePartType.MOTHERBOARD)).ifPresentOrElse(motherboard -> {
        }, () -> messages.add("建议补充主板。"));
        Optional.ofNullable(typed.get(HardwarePartType.MEMORY)).ifPresentOrElse(memory -> {
        }, () -> messages.add("建议补充内存。"));
        Optional.ofNullable(typed.get(HardwarePartType.PSU)).ifPresentOrElse(psu -> {
        }, () -> messages.add("建议补充电源。"));
        Optional.ofNullable(typed.get(HardwarePartType.CASE)).ifPresentOrElse(pcCase -> {
        }, () -> messages.add("建议补充机箱。"));

        HardwarePart cpu = typed.get(HardwarePartType.CPU);
        HardwarePart motherboard = typed.get(HardwarePartType.MOTHERBOARD);
        HardwarePart memory = typed.get(HardwarePartType.MEMORY);
        HardwarePart gpu = typed.get(HardwarePartType.GPU);
        HardwarePart psu = typed.get(HardwarePartType.PSU);
        HardwarePart pcCase = typed.get(HardwarePartType.CASE);
        HardwarePart cooler = typed.get(HardwarePartType.COOLER);

        if (cpu != null && motherboard != null && bothPresent(cpu.getSocketType(), motherboard.getSocketType())
                && !cpu.getSocketType().equalsIgnoreCase(motherboard.getSocketType())) {
            messages.add("CPU 接口 " + cpu.getSocketType() + " 与主板接口 " + motherboard.getSocketType() + " 不匹配。");
            blocked = true;
        }
        if (memory != null && motherboard != null && bothPresent(memory.getMemoryType(), motherboard.getMemoryType())
                && !memory.getMemoryType().equalsIgnoreCase(motherboard.getMemoryType())) {
            messages.add("内存类型 " + memory.getMemoryType() + " 与主板支持的 " + motherboard.getMemoryType() + " 不匹配。");
            blocked = true;
        }
        if (psu != null) {
            int estimatedWattage = parts.stream()
                    .filter(part -> part.getType() != HardwarePartType.PSU)
                    .mapToInt(HardwarePart::getWattage)
                    .sum() + 120;
            if (psu.getWattage() > 0 && psu.getWattage() < estimatedWattage) {
                messages.add("电源额定功率建议至少 " + estimatedWattage + "W，当前为 " + psu.getWattage() + "W。");
                blocked = true;
            }
        }
        if (gpu != null && pcCase != null && gpu.getGpuLengthMm() > 0 && pcCase.getGpuLengthMm() > 0
                && gpu.getGpuLengthMm() > pcCase.getGpuLengthMm()) {
            messages.add("显卡长度 " + gpu.getGpuLengthMm() + "mm 超出机箱支持的 " + pcCase.getGpuLengthMm() + "mm。");
            blocked = true;
        }
        if (cooler != null && pcCase != null && cooler.getCoolerHeightMm() > 0 && pcCase.getCoolerHeightMm() > 0
                && cooler.getCoolerHeightMm() > pcCase.getCoolerHeightMm()) {
            messages.add("散热器高度 " + cooler.getCoolerHeightMm() + "mm 超出机箱支持的 " + pcCase.getCoolerHeightMm() + "mm。");
            blocked = true;
        }
        if (motherboard != null && pcCase != null && bothPresent(motherboard.getFormFactor(), pcCase.getSupportedFormFactors())
                && !pcCase.getSupportedFormFactors().toUpperCase().contains(motherboard.getFormFactor().toUpperCase())) {
            messages.add("主板版型 " + motherboard.getFormFactor() + " 不在机箱支持范围内。");
            blocked = true;
        }

        if (messages.isEmpty()) {
            messages.add("当前选择未发现明显兼容性问题。");
        }
        String status = blocked ? "BLOCK" : messages.size() == 1 && messages.get(0).contains("未发现") ? "PASS" : "WARN";
        return new CompatibilityResult(status, messages);
    }

    private Map<HardwarePartType, HardwarePart> firstByType(List<HardwarePart> parts) {
        Map<HardwarePartType, HardwarePart> typed = new LinkedHashMap<>();
        for (HardwarePart part : parts) {
            typed.putIfAbsent(part.getType(), part);
        }
        return typed;
    }

    private boolean bothPresent(String left, String right) {
        return left != null && !left.isBlank() && right != null && !right.isBlank();
    }

    private String createContent(List<HardwarePart> parts, String note, int totalPrice, int performanceScore, CompatibilityResult compatibility) {
        StringBuilder builder = new StringBuilder();
        for (HardwarePart part : parts) {
            builder.append(typeLabel(part.getType()))
                    .append("：")
                    .append(part.getBrand())
                    .append(" ")
                    .append(part.getModel())
                    .append("，¥")
                    .append(part.getPrice())
                    .append("，性能 ")
                    .append(part.getPerformanceScore())
                    .append(" 分\n");
        }
        builder.append("总价：¥").append(totalPrice).append("\n");
        builder.append("综合性能：").append(performanceScore).append(" 分\n");
        builder.append("兼容性：").append(statusLabel(compatibility.status())).append("\n");
        for (String message : compatibility.messages()) {
            builder.append("- ").append(message).append("\n");
        }
        if (!note.isBlank()) {
            builder.append("备注：").append(note);
        }
        return builder.toString().trim();
    }

    private String typeLabel(HardwarePartType type) {
        return switch (type) {
            case CPU -> "CPU";
            case MOTHERBOARD -> "主板";
            case GPU -> "显卡";
            case MEMORY -> "内存";
            case STORAGE -> "硬盘";
            case PSU -> "电源";
            case CASE -> "机箱";
            case COOLER -> "散热";
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "PASS" -> "兼容";
            case "BLOCK" -> "需调整";
            default -> "需确认";
        };
    }

    private String clean(String value, int maxLength, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private record CompatibilityResult(String status, List<String> messages) {
    }
}
