package cn.cctstudio.smartdiypcassistantbackend.ai;

public record IntentAnalysis(
        String intent,
        String subIntent,
        double confidence,
        String reasoning,
        String extractedDataJson,
        boolean safetyCheckRequired,
        String safetyRiskLevel
) {

    public static IntentAnalysis fallback(String subIntent, String reasoning) {
        return new IntentAnalysis(
                categoryOf(subIntent),
                subIntent,
                0.72,
                reasoning,
                "{}",
                needsSafetyCheck(subIntent),
                needsSafetyCheck(subIntent) ? "WARN" : "PASS"
        );
    }

    private static String categoryOf(String subIntent) {
        return switch (subIntent) {
            case "build_request", "clarify_requirement" -> "配置生成";
            case "build_modify", "build_compare" -> "方案调整";
            case "compatibility_check", "risk_explain" -> "兼容风险";
            case "preference_record", "history_recall", "recommendation_retry" -> "账户偏好";
            default -> "普通对话";
        };
    }

    private static boolean needsSafetyCheck(String subIntent) {
        return switch (subIntent) {
            case "build_request", "build_modify", "build_compare", "compatibility_check", "risk_explain", "recommendation_retry" -> true;
            default -> false;
        };
    }
}
