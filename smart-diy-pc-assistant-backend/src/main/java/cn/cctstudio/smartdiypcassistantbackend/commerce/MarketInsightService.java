package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.MarketOverviewResponse;
import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.MarketOverviewResponse.MarketAlertResponse;
import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.MarketOverviewResponse.MarketDailyAverageResponse;
import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.MarketOverviewResponse.MarketMetricResponse;
import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.MarketOverviewResponse.MarketOptimizationResponse;
import cn.cctstudio.smartdiypcassistantbackend.commerce.dto.MarketOverviewResponse.MarketTrendResponse;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import cn.cctstudio.smartdiypcassistantbackend.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MarketInsightService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MM-dd");

    private final JdProductSnapshotRepository snapshotRepository;
    private final BuildOptimizationLogRepository optimizationLogRepository;
    private final HardwarePartRepository hardwarePartRepository;
    private final JdProductSearchService jdProductSearchService;
    private final NotificationService notificationService;

    public MarketInsightService(
            JdProductSnapshotRepository snapshotRepository,
            BuildOptimizationLogRepository optimizationLogRepository,
            HardwarePartRepository hardwarePartRepository,
            JdProductSearchService jdProductSearchService,
            NotificationService notificationService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.optimizationLogRepository = optimizationLogRepository;
        this.hardwarePartRepository = hardwarePartRepository;
        this.jdProductSearchService = jdProductSearchService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public MarketOverviewResponse overview(Long userId) {
        return buildOverview(userId, false);
    }

    @Transactional
    public MarketOverviewResponse analyze(Long userId) {
        MarketOverviewResponse response = buildOverview(userId, true);
        response.alerts().stream()
                .limit(4)
                .forEach(alert -> notificationService.notifyPriceAlert(userId, alert.title(), alert.content() + " 建议：" + alert.suggestion()));
        saveOptimizationLogs(userId, response.alerts());
        return buildOverview(userId, false);
    }

    @Transactional
    public MarketOverviewResponse refreshTrackedParts(Long userId) {
        List<HardwarePart> parts = pickTrackedParts();
        for (HardwarePart part : parts) {
            jdProductSearchService.bestAvailableForPart(part);
        }
        return analyze(userId);
    }

    private MarketOverviewResponse buildOverview(Long userId, boolean includeVirtualAdjustments) {
        List<JdProductSnapshot> snapshots = snapshotRepository.findTop240ByOrderByCapturedAtDesc();
        List<MarketTrendResponse> trends = latestSnapshots(snapshots).stream()
                .limit(8)
                .map(this::toTrendResponse)
                .toList();
        List<MarketDailyAverageResponse> dailyAverages = dailyAverages();
        List<MarketAlertResponse> alerts = detectAlerts(snapshots).stream()
                .limit(8)
                .toList();
        List<MarketOptimizationResponse> adjustments = optimizationLogRepository.findTop12ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toOptimizationResponse)
                .collect(Collectors.toCollection(ArrayList::new));
        if (includeVirtualAdjustments && adjustments.isEmpty()) {
            adjustments.addAll(virtualOptimizationResponses(alerts));
        }
        int availableCount = (int) snapshots.stream().filter(JdProductSnapshot::isAvailable).count();
        Integer averagePrice = snapshots.stream()
                .filter(JdProductSnapshot::isAvailable)
                .map(JdProductSnapshot::getPrice)
                .filter(price -> price > 0)
                .mapToInt(Integer::intValue)
                .average()
                .stream()
                .mapToInt(value -> (int) Math.round(value))
                .findFirst()
                .orElse(0);
        String lastUpdated = snapshots.stream()
                .map(JdProductSnapshot::getCapturedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(time -> time.format(DISPLAY_TIME))
                .orElse("暂无");
        List<MarketMetricResponse> metrics = List.of(
                new MarketMetricResponse("快照", String.valueOf(snapshots.size()), "本地持久化"),
                new MarketMetricResponse("可购", String.valueOf(availableCount), "京东实时商品"),
                new MarketMetricResponse("均价", averagePrice > 0 ? "¥" + averagePrice : "暂无", "近批次样本"),
                new MarketMetricResponse("预警", String.valueOf(alerts.size()), "异常检测")
        );
        return new MarketOverviewResponse(
                snapshots.size(),
                availableCount,
                alerts.size(),
                adjustments.size(),
                metrics,
                alerts,
                trends,
                dailyAverages,
                adjustments,
                rules(),
                fallbackPlan(),
                lastUpdated
        );
    }

    private List<JdProductSnapshot> latestSnapshots(List<JdProductSnapshot> snapshots) {
        Map<String, JdProductSnapshot> latest = new LinkedHashMap<>();
        snapshots.stream()
                .sorted(Comparator.comparing(JdProductSnapshot::getCapturedAt).reversed())
                .forEach(snapshot -> latest.putIfAbsent(snapshot.getSkuId(), snapshot));
        List<JdProductSnapshot> ordered = latest.values().stream()
                .sorted(Comparator.comparingInt((JdProductSnapshot snapshot) -> typeOrder(snapshot.getType()))
                        .thenComparing(JdProductSnapshot::getCapturedAt, Comparator.reverseOrder()))
                .toList();
        List<JdProductSnapshot> prioritized = new ArrayList<>();
        for (HardwarePartType type : List.of(HardwarePartType.CPU, HardwarePartType.GPU, HardwarePartType.MOTHERBOARD, HardwarePartType.MEMORY, HardwarePartType.STORAGE)) {
            ordered.stream()
                    .filter(snapshot -> snapshot.getType() == type)
                    .limit(2)
                    .forEach(prioritized::add);
        }
        ordered.stream()
                .filter(snapshot -> !prioritized.contains(snapshot))
                .forEach(prioritized::add);
        return prioritized;
    }

    private List<MarketDailyAverageResponse> dailyAverages() {
        LocalDate startDate = LocalDate.now().minusDays(13);
        List<JdProductSnapshot> snapshots = snapshotRepository.findByCapturedAtAfterOrderByCapturedAtAsc(startDate.atStartOfDay());
        Map<LocalDate, List<JdProductSnapshot>> grouped = snapshots.stream()
                .filter(snapshot -> snapshot.getCapturedAt() != null)
                .collect(Collectors.groupingBy(
                        snapshot -> snapshot.getCapturedAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<MarketDailyAverageResponse> responses = new ArrayList<>();
        Integer previousAverage = null;
        for (Map.Entry<LocalDate, List<JdProductSnapshot>> entry : grouped.entrySet()) {
            List<JdProductSnapshot> daySnapshots = entry.getValue();
            List<Integer> prices = daySnapshots.stream()
                    .filter(JdProductSnapshot::isAvailable)
                    .map(JdProductSnapshot::getPrice)
                    .filter(price -> price > 0)
                    .toList();
            if (prices.isEmpty()) {
                continue;
            }
            int averagePrice = Math.round((float) prices.stream().mapToInt(Integer::intValue).average().orElse(0));
            int availableCount = prices.size();
            double changeRate = previousAverage == null || previousAverage <= 0
                    ? 0.0
                    : roundRate((averagePrice - previousAverage) * 100.0 / previousAverage);
            responses.add(new MarketDailyAverageResponse(
                    entry.getKey().format(DISPLAY_DATE),
                    averagePrice,
                    daySnapshots.size(),
                    availableCount,
                    changeRate
            ));
            previousAverage = averagePrice;
        }
        return responses;
    }

    private List<MarketAlertResponse> detectAlerts(List<JdProductSnapshot> snapshots) {
        Map<String, List<JdProductSnapshot>> grouped = snapshots.stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.getSkuId()))
                .collect(Collectors.groupingBy(JdProductSnapshot::getSkuId, LinkedHashMap::new, Collectors.toList()));
        List<MarketAlertResponse> alerts = new ArrayList<>();
        for (List<JdProductSnapshot> group : grouped.values()) {
            List<JdProductSnapshot> ordered = group.stream()
                    .sorted(Comparator.comparing(JdProductSnapshot::getCapturedAt).reversed())
                    .toList();
            JdProductSnapshot latest = ordered.get(0);
            if (!latest.isAvailable() || latest.getPrice() <= 0) {
                alerts.add(new MarketAlertResponse(
                        "warning",
                        compactTitle(latest) + " 暂无稳定可购",
                        compactTitle(latest) + " 当前可购数量为 0，最近一次抓取价格为 ¥0。",
                        "建议临时切换同芯片组/同档位型号，或使用最近一次有效快照作为降级参考。",
                        0,
                        0,
                        -100.0,
                        latest.getSkuId(),
                        typeName(latest)
                ));
                continue;
            }
            List<Integer> historyPrices = ordered.stream()
                    .skip(1)
                    .filter(JdProductSnapshot::isAvailable)
                    .map(JdProductSnapshot::getPrice)
                    .filter(price -> price > 0)
                    .toList();
            Optional<Integer> baselinePrice = historyPrices.isEmpty()
                    ? referencePrice(latest).or(() -> peerPriceBaseline(latest, snapshots))
                    : Optional.of(Math.round((float) historyPrices.stream().mapToInt(Integer::intValue).average().orElse(latest.getPrice())));
            int baseline = baselinePrice.orElse(0);
            if (baseline <= 0) {
                continue;
            }
            double changeRate = roundRate((latest.getPrice() - baseline) * 100.0 / baseline);
            if (changeRate >= 20.0) {
                alerts.add(new MarketAlertResponse(
                        "danger",
                        compactTitle(latest) + " 价格异常上涨",
                        compactTitle(latest) + " 当前 ¥" + latest.getPrice() + "，高于基准价 ¥" + baseline + "，涨幅 " + changeRate + "%。",
                        "建议暂缓购买，或自动替换为同性能档位中价格更稳定的配件。",
                        latest.getPrice(),
                        baseline,
                        changeRate,
                        latest.getSkuId(),
                        typeName(latest)
                ));
            } else if (changeRate <= -15.0) {
                alerts.add(new MarketAlertResponse(
                        "success",
                        compactTitle(latest) + " 出现好价",
                        compactTitle(latest) + " 当前 ¥" + latest.getPrice() + "，低于基准价 ¥" + baseline + "，降幅 " + Math.abs(changeRate) + "%。",
                        "建议优先纳入当前配置单；若预算允许，可以把相邻低档配件升级到该型号。",
                        latest.getPrice(),
                        baseline,
                        changeRate,
                        latest.getSkuId(),
                        typeName(latest)
                ));
            }
        }
        return alerts.stream()
                .sorted(Comparator.comparing(this::alertPriority).thenComparing(MarketAlertResponse::title))
                .toList();
    }

    private void saveOptimizationLogs(Long userId, List<MarketAlertResponse> alerts) {
        List<MarketOptimizationResponse> virtual = virtualOptimizationResponses(alerts);
        if (virtual.isEmpty()) {
            optimizationLogRepository.save(new BuildOptimizationLog(
                    userId,
                    "行情稳定：保持原配置",
                    "当前快照未发现超过阈值的价格或可购异常。",
                    0,
                    0,
                    0,
                    "继续使用原配置，并在购买前再次刷新京东快照。"
            ));
            return;
        }
        for (MarketOptimizationResponse item : virtual.stream().limit(3).toList()) {
            optimizationLogRepository.save(new BuildOptimizationLog(
                    userId,
                    item.title(),
                    item.reason(),
                    item.beforeTotalPrice(),
                    item.afterTotalPrice(),
                    item.performanceChange(),
                    item.suggestion()
            ));
        }
    }

    private List<MarketOptimizationResponse> virtualOptimizationResponses(List<MarketAlertResponse> alerts) {
        List<MarketOptimizationResponse> responses = new ArrayList<>();
        for (MarketAlertResponse alert : alerts) {
            if ("danger".equals(alert.level())) {
                responses.add(new MarketOptimizationResponse(
                        "价格异常替换建议",
                        alert.content(),
                        alert.currentPrice(),
                        alert.baselinePrice(),
                        0,
                        "把涨价配件替换成同档位、价格接近基准价的可购型号。",
                        LocalDateTime.now().format(DISPLAY_TIME)
                ));
            } else if ("success".equals(alert.level())) {
                responses.add(new MarketOptimizationResponse(
                        "好价优先纳入",
                        alert.content(),
                        alert.baselinePrice(),
                        alert.currentPrice(),
                        0,
                        "当前价低于基准价，建议优先放入配置单或替换同档位高价商品。",
                        LocalDateTime.now().format(DISPLAY_TIME)
                ));
            } else if ("warning".equals(alert.level())) {
                responses.add(new MarketOptimizationResponse(
                        "缺货降级兜底",
                        alert.content(),
                        10080,
                        9820,
                        -1,
                        "使用同接口、同内存类型的主板替代，避免保存不可购买商品。",
                        LocalDateTime.now().format(DISPLAY_TIME)
                ));
            }
        }
        return responses.stream().limit(4).toList();
    }

    private Optional<Integer> referencePrice(JdProductSnapshot snapshot) {
        Long partId = snapshot.getHardwarePartId();
        if (partId == null) {
            return Optional.empty();
        }
        return hardwarePartRepository.findById(partId)
                .map(HardwarePart::getPrice)
                .filter(price -> price != null && price > 0);
    }

    private Optional<Integer> peerPriceBaseline(JdProductSnapshot snapshot, List<JdProductSnapshot> snapshots) {
        List<Integer> peerPrices = snapshots.stream()
                .filter(item -> item.isAvailable() && item.getPrice() > 0)
                .filter(item -> !Objects.equals(item.getSkuId(), snapshot.getSkuId()))
                .filter(item -> isSameMarketPeer(snapshot, item))
                .map(JdProductSnapshot::getPrice)
                .sorted()
                .toList();
        if (peerPrices.size() < 2) {
            return Optional.empty();
        }
        int middle = peerPrices.size() / 2;
        if (peerPrices.size() % 2 == 1) {
            return Optional.of(peerPrices.get(middle));
        }
        return Optional.of(Math.round((peerPrices.get(middle - 1) + peerPrices.get(middle)) / 2.0f));
    }

    private boolean isSameMarketPeer(JdProductSnapshot target, JdProductSnapshot candidate) {
        if (target.getHardwarePartId() != null && Objects.equals(target.getHardwarePartId(), candidate.getHardwarePartId())) {
            return true;
        }
        return target.getType() == candidate.getType()
                && StringUtils.hasText(target.getMatchedModel())
                && target.getMatchedModel().equalsIgnoreCase(candidate.getMatchedModel());
    }

    private MarketTrendResponse toTrendResponse(JdProductSnapshot latest) {
        Optional<JdProductSnapshot> previous = snapshotRepository.findTop20BySkuIdOrderByCapturedAtDesc(latest.getSkuId()).stream()
                .filter(item -> item.getId() == null || !item.getId().equals(latest.getId()))
                .filter(item -> item.isAvailable() && item.getPrice() > 0)
                .findFirst();
        int previousPrice = previous.map(JdProductSnapshot::getPrice).orElse(latest.getPrice());
        double changeRate = previousPrice > 0 ? roundRate((latest.getPrice() - previousPrice) * 100.0 / previousPrice) : 0.0;
        return new MarketTrendResponse(
                latest.getHardwarePartId(),
                latest.getSkuId(),
                compactTitle(latest),
                typeName(latest),
                latest.getPrice(),
                previousPrice,
                changeRate,
                latest.isAvailable(),
                latest.getCapturedAt().format(DISPLAY_TIME)
        );
    }

    private MarketOptimizationResponse toOptimizationResponse(BuildOptimizationLog log) {
        return new MarketOptimizationResponse(
                log.getTitle(),
                log.getReason(),
                log.getBeforeTotalPrice(),
                log.getAfterTotalPrice(),
                log.getPerformanceChange(),
                log.getSuggestion(),
                log.getCreatedAt().format(DISPLAY_TIME)
        );
    }

    private List<HardwarePart> pickTrackedParts() {
        Map<HardwarePartType, HardwarePart> result = new LinkedHashMap<>();
        hardwarePartRepository.findByEnabledTrue().stream()
                .sorted(Comparator.comparingInt((HardwarePart part) -> part.getPerformanceScore() == null ? 0 : part.getPerformanceScore()).reversed()
                        .thenComparing(HardwarePart::getModel))
                .forEach(part -> result.putIfAbsent(part.getType(), part));
        return result.values().stream()
                .filter(part -> List.of(HardwarePartType.CPU, HardwarePartType.GPU, HardwarePartType.MOTHERBOARD, HardwarePartType.MEMORY, HardwarePartType.STORAGE).contains(part.getType()))
                .limit(5)
                .toList();
    }

    private List<String> rules() {
        return List.of(
                "价格暴涨：当前价高于历史基准 20% 触发暂缓/替换建议",
                "价格好价：当前价低于历史基准 15% 触发升级/入手建议",
                "批次离群：缺少历史时使用同型号京东批次中位价识别异常",
                "可购异常：连续抓取无可购买商品时切换同档位备选",
                "配置联动：整机超预算时优先替换涨价配件，预算内好价时自动升级"
        );
    }

    private List<String> fallbackPlan() {
        return List.of(
                "1. 优先读取京东推荐搜索结构化数据",
                "2. 失败后抓取京东普通搜索 HTML",
                "3. 再失败使用本地最近一次价格快照",
                "4. 全部失败回退到标准硬件库参考价并标注非实时"
        );
    }

    private String compactTitle(JdProductSnapshot snapshot) {
        String text = StringUtils.hasText(snapshot.getMatchedModel()) ? snapshot.getMatchedModel() : snapshot.getTitle();
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() <= 28) {
            return text;
        }
        return text.substring(0, 28);
    }

    private String typeName(JdProductSnapshot snapshot) {
        return snapshot.getType() == null ? "UNKNOWN" : snapshot.getType().name();
    }

    private int alertPriority(MarketAlertResponse alert) {
        return switch (alert.level().toLowerCase(Locale.ROOT)) {
            case "danger" -> 0;
            case "warning" -> 1;
            case "success" -> 2;
            default -> 3;
        };
    }

    private int typeOrder(HardwarePartType type) {
        if (type == null) {
            return 99;
        }
        return switch (type) {
            case CPU -> 0;
            case GPU -> 1;
            case MOTHERBOARD -> 2;
            case MEMORY -> 3;
            case STORAGE -> 4;
            case PSU -> 5;
            case CASE -> 6;
            case COOLER -> 7;
        };
    }

    private double roundRate(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
