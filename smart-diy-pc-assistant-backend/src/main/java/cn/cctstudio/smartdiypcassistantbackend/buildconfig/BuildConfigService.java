package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigRequest;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildPartSelectionRequest;
import cn.cctstudio.smartdiypcassistantbackend.commerce.JdProduct;
import cn.cctstudio.smartdiypcassistantbackend.commerce.JdProductSearchService;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import cn.cctstudio.smartdiypcassistantbackend.user.UserAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.security.SecureRandom;
import java.util.stream.Collectors;

@Service
public class BuildConfigService {

    private static final String SERIAL_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom SERIAL_RANDOM = new SecureRandom();

    private final BuildConfigRepository buildConfigRepository;
    private final BuildConfigFavoriteRepository buildConfigFavoriteRepository;
    private final BuildConfigAiReviewRepository buildConfigAiReviewRepository;
    private final HardwarePartRepository hardwarePartRepository;
    private final UserAccessService userAccessService;
    private final JdProductSearchService jdProductSearchService;
    private final ObjectMapper objectMapper;

    public BuildConfigService(
            BuildConfigRepository buildConfigRepository,
            BuildConfigFavoriteRepository buildConfigFavoriteRepository,
            BuildConfigAiReviewRepository buildConfigAiReviewRepository,
            HardwarePartRepository hardwarePartRepository,
            UserAccessService userAccessService,
            JdProductSearchService jdProductSearchService,
            ObjectMapper objectMapper
    ) {
        this.buildConfigRepository = buildConfigRepository;
        this.buildConfigFavoriteRepository = buildConfigFavoriteRepository;
        this.buildConfigAiReviewRepository = buildConfigAiReviewRepository;
        this.hardwarePartRepository = hardwarePartRepository;
        this.userAccessService = userAccessService;
        this.jdProductSearchService = jdProductSearchService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<BuildConfigResponse> list(Long userId, String segment) {
        if ("favorite".equals(segment)) {
            return listFavoriteByUser(userId);
        }
        List<BuildConfig> configs = switch (segment == null ? "mine" : segment) {
            case "all" -> buildConfigRepository.findByUserIdOrderByUpdatedAtDesc(userId);
            case "ai" -> buildConfigRepository.findByUserIdAndSourceOrderByUpdatedAtDesc(userId, BuildConfigSource.AI);
            default -> buildConfigRepository.findByUserIdAndSourceOrderByUpdatedAtDesc(userId, BuildConfigSource.MANUAL);
        };
        return configs.stream().map(config -> toResponse(config, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<BuildConfigResponse> listFavoriteByUser(Long userId) {
        return listFavoriteByUser(userId, userId);
    }

    @Transactional(readOnly = true)
    public List<BuildConfigResponse> listFavoriteByUser(Long favoriteOwnerUserId, Long viewerUserId) {
        List<BuildConfigFavorite> favorites = buildConfigFavoriteRepository.findByUserIdOrderByCreatedAtDesc(favoriteOwnerUserId);
        List<Long> favoriteIds = favorites.stream()
                .map(BuildConfigFavorite::getBuildConfigId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, BuildConfig> configMap = favoriteIds.isEmpty()
                ? Map.of()
                : buildConfigRepository.findByIdIn(favoriteIds).stream()
                .collect(Collectors.toMap(BuildConfig::getId, item -> item));
        List<BuildConfigResponse> result = new ArrayList<>();
        for (Long id : favoriteIds) {
            BuildConfig config = configMap.get(id);
            if (config != null) {
                result.add(toResponse(config, viewerUserId));
            }
        }
        for (BuildConfig config : buildConfigRepository.findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(favoriteOwnerUserId)) {
            if (!favoriteIds.contains(config.getId())) {
                result.add(toResponse(config, viewerUserId));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BuildConfigResponse get(Long userId, Long id) {
        return toResponse(findOwned(id, userId), userId);
    }

    @Transactional(readOnly = true)
    public BuildConfigResponse getPublic(Long id) {
        return buildConfigRepository.findById(id)
                .map(config -> toResponse(config, null))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public BuildConfigResponse getPublic(Long viewerUserId, Long id) {
        return buildConfigRepository.findById(id)
                .map(config -> toResponse(config, viewerUserId))
                .orElseThrow(() -> new BusinessException("配置不存在"));
    }

    @Transactional
    public BuildConfigResponse create(Long userId, BuildConfigRequest request) {
        userAccessService.requireAvailable(userId);
        BuildConfig config = new BuildConfig(userId, "", "", "", "", 0, 0, "WARN", "", "", "", BuildConfigSource.MANUAL);
        config.assignSerialNumber(generateUniqueSerialNumber());
        applyConfig(config, request);
        return toResponse(buildConfigRepository.save(config), userId);
    }

    @Transactional
    public BuildConfigResponse update(Long userId, Long id, BuildConfigRequest request) {
        userAccessService.requireAvailable(userId);
        BuildConfig config = findOwned(id, userId);
        applyConfig(config, request);
        buildConfigAiReviewRepository.deleteByBuildConfigId(id);
        return toResponse(config, userId);
    }

    private void applyConfig(BuildConfig config, BuildConfigRequest request) {
        String title = clean(request.title(), 80, "未命名配置");
        String note = clean(request.note(), 500, "");
        List<BuildPartSelection> selections = resolveSelections(request);
        List<HardwarePart> parts = resolveParts(selections.stream().map(BuildPartSelection::hardwarePartId).toList());
        selections = fillMissingProductSnapshots(parts, selections);
        int totalPrice = calculateTotalPrice(parts, selections);
        int performanceScore = calculatePerformanceScore(parts);
        CompatibilityResult compatibility = checkCompatibility(parts);
        if ("BLOCK".equals(compatibility.status())) {
            throw new BusinessException("存在兼容性问题，请调整红色标记的硬件后再保存");
        }
        String summary = "¥" + totalPrice + " · 性能 " + performanceScore + " 分 · " + statusLabel(compatibility.status());
        String content = createContent(parts, selections, note, totalPrice, performanceScore, compatibility);
        BuildConfigSource source = request.source() == null ? BuildConfigSource.MANUAL : request.source();
        config.updateConfig(
                title,
                note,
                serializePartIds(parts),
                serializeSelections(selections),
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
        BuildConfig config = buildConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException("配置不存在"));
        Optional<BuildConfigFavorite> favoriteRecord = buildConfigFavoriteRepository.findByUserIdAndBuildConfigId(userId, id);
        if (favorite && favoriteRecord.isEmpty()) {
            buildConfigFavoriteRepository.save(new BuildConfigFavorite(userId, id));
        }
        if (!favorite) {
            favoriteRecord.ifPresent(buildConfigFavoriteRepository::delete);
        }
        if (Objects.equals(config.getUserId(), userId)) {
            config.updateFavorite(favorite);
        }
        return toResponse(config, userId);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        BuildConfig config = findOwned(id, userId);
        buildConfigFavoriteRepository.deleteByBuildConfigId(id);
        buildConfigAiReviewRepository.deleteByBuildConfigId(id);
        buildConfigRepository.delete(config);
    }

    private BuildConfigResponse toResponse(BuildConfig config) {
        return toResponse(config, null);
    }

    private BuildConfigResponse toResponse(BuildConfig config, Long viewerUserId) {
        List<HardwarePart> parts = resolveParts(parsePartIds(config.getPartIds()), false);
        List<BuildPartSelection> selections = fillMissingProductSnapshots(parts, parseSelections(config.getPartSelections()));
        boolean favorite = config.isFavorite();
        if (viewerUserId != null) {
            favorite = buildConfigFavoriteRepository.existsByUserIdAndBuildConfigId(viewerUserId, config.getId())
                    || (Objects.equals(config.getUserId(), viewerUserId) && config.isFavorite());
        }
        Optional<BuildConfigAiReview> aiReview = viewerUserId == null
                ? Optional.empty()
                : buildConfigAiReviewRepository.findByBuildConfigIdAndUserId(config.getId(), viewerUserId);
        return BuildConfigResponse.from(
                config,
                parts,
                selections,
                favorite,
                aiReview.map(BuildConfigAiReview::getReviewContent).orElse(""),
                aiReview.map(BuildConfigAiReview::getUpdatedAt).orElse(null)
        );
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

    private List<BuildPartSelection> resolveSelections(BuildConfigRequest request) {
        if (request.partSelections() != null && !request.partSelections().isEmpty()) {
            return request.partSelections().stream()
                    .filter(item -> item != null && item.hardwarePartId() != null)
                    .map(item -> new BuildPartSelection(
                            item.hardwarePartId(),
                            cleanSnapshotText(item.title(), 240),
                            item.price() == null ? 0 : item.price(),
                            cleanSnapshotText(item.image(), 500),
                            cleanSnapshotText(item.link(), 500),
                            cleanSnapshotText(item.skuId(), 80),
                            cleanSnapshotText(item.matchedModel(), 120)
                    ))
                    .toList();
        }
        if (request.partIds() == null || request.partIds().isEmpty()) {
            throw new BusinessException("请至少选择一个硬件");
        }
        return request.partIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> new BuildPartSelection(id, "", 0, "", "", "", ""))
                .toList();
    }

    private List<BuildPartSelection> fillMissingProductSnapshots(List<HardwarePart> parts, List<BuildPartSelection> selections) {
        Map<Long, BuildPartSelection> selectionMap = selections.stream()
                .filter(item -> item.hardwarePartId() != null)
                .collect(Collectors.toMap(BuildPartSelection::hardwarePartId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<BuildPartSelection> filled = new ArrayList<>();
        for (HardwarePart part : parts) {
            BuildPartSelection current = selectionMap.get(part.getId());
            if (isIncludedInCpuBundle(current)) {
                filled.add(current);
                continue;
            }
            if (hasCompleteProductSnapshot(current)) {
                filled.add(current);
                continue;
            }
            JdProduct product = jdProductSearchService.bestForPart(part);
            if (!StringUtils.hasText(product.title()) && current != null) {
                filled.add(current);
                continue;
            }
            filled.add(new BuildPartSelection(
                    part.getId(),
                    firstSnapshotText(product.title(), current == null ? "" : current.title()),
                    product.price() != null && product.price() > 0 ? product.price() : current == null ? 0 : current.price(),
                    firstSnapshotText(product.image(), current == null ? "" : current.image()),
                    firstSnapshotText(product.link(), current == null ? "" : current.link()),
                    firstSnapshotText(product.skuId(), current == null ? "" : current.skuId()),
                    firstSnapshotText(product.matchedModel(), current == null ? part.getModel() : current.matchedModel())
            ));
        }
        return filled;
    }

    private boolean isIncludedInCpuBundle(BuildPartSelection selection) {
        return selection != null
                && selection.price() != null
                && selection.price() == 0
                && StringUtils.hasText(selection.title())
                && selection.title().contains("已包含在 CPU 板U套装中");
    }

    private boolean hasCompleteProductSnapshot(BuildPartSelection selection) {
        return selection != null
                && selection.price() != null
                && selection.price() > 0
                && StringUtils.hasText(selection.title())
                && StringUtils.hasText(selection.image())
                && StringUtils.hasText(selection.link())
                && StringUtils.hasText(selection.skuId());
    }

    private String firstSnapshotText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback == null ? "" : fallback;
    }

    private List<BuildPartSelection> parseSelections(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<BuildPartSelection>>() {
            });
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String serializeSelections(List<BuildPartSelection> selections) {
        try {
            return objectMapper.writeValueAsString(selections);
        } catch (JsonProcessingException ignored) {
            return "[]";
        }
    }

    private String serializePartIds(List<HardwarePart> parts) {
        return parts.stream()
                .map(part -> String.valueOf(part.getId()))
                .collect(Collectors.joining(","));
    }

    private int calculateTotalPrice(List<HardwarePart> parts, List<BuildPartSelection> selections) {
        Map<Long, Integer> priceMap = selections.stream()
                .filter(item -> item.hardwarePartId() != null)
                .collect(Collectors.toMap(
                        BuildPartSelection::hardwarePartId,
                        item -> item.price() == null ? 0 : item.price(),
                        (left, right) -> left
                ));
        int total = 0;
        for (HardwarePart part : parts) {
            int productPrice = priceMap.getOrDefault(part.getId(), 0);
            total += Math.max(0, productPrice);
        }
        return total;
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
        HardwarePart psu = typed.get(HardwarePartType.PSU);
        HardwarePart pcCase = typed.get(HardwarePartType.CASE);

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

    private String createContent(List<HardwarePart> parts, List<BuildPartSelection> selections, String note, int totalPrice, int performanceScore, CompatibilityResult compatibility) {
        Map<Long, BuildPartSelection> selectionMap = selections.stream()
                .filter(item -> item.hardwarePartId() != null)
                .collect(Collectors.toMap(BuildPartSelection::hardwarePartId, item -> item, (left, right) -> left));
        StringBuilder builder = new StringBuilder();
        for (HardwarePart part : parts) {
            BuildPartSelection selection = selectionMap.get(part.getId());
            int price = selection != null && selection.price() != null && selection.price() > 0 ? selection.price() : 0;
            boolean includedInCpuBundle = isIncludedInCpuBundle(selection);
            String productTitle = selection != null && StringUtils.hasText(selection.title()) ? selection.title() : part.getModel();
            builder.append(typeLabel(part.getType()))
                    .append("：")
                    .append(productTitle);
            if (!productTitle.equals(part.getModel())) {
                builder.append("（标准型号：").append(part.getModel()).append("）");
            }
            if (price > 0) {
                builder.append("，¥").append(price);
            } else if (includedInCpuBundle) {
                builder.append("，已包含在板U套装价格中");
            } else {
                builder.append("，暂无京东价格");
            }
            if (!hasPerformanceScore(part.getType())) {
                builder.append("，不参与性能评分");
            } else {
                builder.append("，性能 ")
                        .append(part.getPerformanceScore())
                        .append(" 分");
            }
            if (selection != null && StringUtils.hasText(selection.link())) {
                builder.append("，京东：").append(selection.link());
            }
            builder.append("\n");
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

    private boolean hasPerformanceScore(HardwarePartType type) {
        return type == HardwarePartType.CPU
                || type == HardwarePartType.GPU
                || type == HardwarePartType.MEMORY
                || type == HardwarePartType.STORAGE;
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

    private String cleanSnapshotText(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String generateUniqueSerialNumber() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String serial = "#" + randomSerialBody();
            if (!buildConfigRepository.existsBySerialNumber(serial)) {
                return serial;
            }
        }
        String fallback = Long.toString(Math.abs(SERIAL_RANDOM.nextLong()), 36).toUpperCase();
        return "#" + (fallback + "00000000").substring(0, 8);
    }

    private String randomSerialBody() {
        StringBuilder builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            builder.append(SERIAL_ALPHABET.charAt(SERIAL_RANDOM.nextInt(SERIAL_ALPHABET.length())));
        }
        return builder.toString();
    }

    private record CompatibilityResult(String status, List<String> messages) {
    }
}
