package cn.cctstudio.smartdiypcassistantbackend.ai;

public record IntentAnalysis(
        String intent,
        String subIntent,
        double confidence,
        String reasoning,
        String extractedDataJson,
        boolean safetyCheckRequired,
        String safetyRiskLevel,
        AiToolAction toolAction,
        double toolConfidence,
        String toolReason,
        String toolArgsJson
) {

    public static IntentAnalysis fallback(String subIntent, String reasoning) {
        AiToolAction toolAction = defaultToolAction(subIntent);
        return new IntentAnalysis(
                categoryOf(subIntent),
                subIntent,
                0.72,
                reasoning,
                "{}",
                needsSafetyCheck(subIntent),
                needsSafetyCheck(subIntent) ? "WARN" : "PASS",
                toolAction,
                0.62,
                reasoning,
                "{}"
        );
    }

    public IntentAnalysis withToolAction(AiToolAction action, double confidence, String reason) {
        return new IntentAnalysis(
                intent,
                subIntent,
                confidence(),
                reasoning,
                extractedDataJson,
                safetyCheckRequired,
                safetyRiskLevel,
                action == null ? AiToolAction.DIRECT_ANSWER : action,
                Math.max(0, Math.min(1, confidence)),
                reason == null ? "" : reason,
                toolArgsJson
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

    private static AiToolAction defaultToolAction(String subIntent) {
        return switch (subIntent) {
            case "build_request" -> AiToolAction.GENERATE_BUILD;
            case "build_modify", "recommendation_retry" -> AiToolAction.MODIFY_BUILD;
            case "build_compare", "compatibility_check", "risk_explain", "preference_record", "history_recall", "chitchat" -> AiToolAction.DIRECT_ANSWER;
            default -> AiToolAction.DIRECT_ANSWER;
        };
    }

    private static boolean needsSafetyCheck(String subIntent) {
        return switch (subIntent) {
            case "build_request", "build_modify", "build_compare", "compatibility_check", "risk_explain", "recommendation_retry" -> true;
            default -> false;
        };
    }
}
