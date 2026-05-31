package cn.cctstudio.smartdiypcassistantbackend.commerce.dto;

import java.util.List;

public record MarketOverviewResponse(
        Integer snapshotCount,
        Integer availableCount,
        Integer alertCount,
        Integer adjustmentCount,
        List<MarketMetricResponse> metrics,
        List<MarketAlertResponse> alerts,
        List<MarketTrendResponse> trends,
        List<MarketDailyAverageResponse> dailyAverages,
        List<MarketOptimizationResponse> adjustments,
        List<String> rules,
        List<String> fallbackPlan,
        String lastUpdated
) {

    public record MarketMetricResponse(
            String label,
            String value,
            String trend
    ) {
    }

    public record MarketAlertResponse(
            String level,
            String title,
            String content,
            String suggestion,
            Integer currentPrice,
            Integer baselinePrice,
            Double changeRate,
            String skuId,
            String type
    ) {
    }

    public record MarketTrendResponse(
            Long hardwarePartId,
            String skuId,
            String title,
            String type,
            Integer currentPrice,
            Integer previousPrice,
            Double changeRate,
            Boolean available,
            String capturedAt
    ) {
    }

    public record MarketDailyAverageResponse(
            String date,
            Integer averagePrice,
            Integer snapshotCount,
            Integer availableCount,
            Double changeRate
    ) {
    }

    public record MarketOptimizationResponse(
            String title,
            String reason,
            Integer beforeTotalPrice,
            Integer afterTotalPrice,
            Integer performanceChange,
            String suggestion,
            String createdAt
    ) {
    }
}
