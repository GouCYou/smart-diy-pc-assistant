package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.config.properties.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class IntentAnalysisService {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public IntentAnalysisService(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public IntentAnalysis analyze(String message) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return fallbackAnalyze(message);
        }
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .apiKey(properties.apiKey())
                    .baseUrl(properties.baseUrl())
                    .modelName(properties.modelName())
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .build();
            String raw = model.generate(buildPrompt(message));
            JsonNode json = objectMapper.readTree(extractJson(raw));
            String subIntent = json.path("subIntent").asText("chitchat");
            return new IntentAnalysis(
                    json.path("intent").asText("普通对话"),
                    subIntent,
                    json.path("confidence").asDouble(0.7),
                    json.path("reasoning").asText("已完成意图识别"),
                    json.path("extractedData").isMissingNode() ? "{}" : json.path("extractedData").toString(),
                    json.path("safetyCheck").path("required").asBoolean(needsSafetyCheck(subIntent)),
                    json.path("safetyCheck").path("riskLevel").asText(needsSafetyCheck(subIntent) ? "WARN" : "PASS")
            );
        } catch (RuntimeException exception) {
            return fallbackAnalyze(message);
        } catch (Exception exception) {
            return fallbackAnalyze(message);
        }
    }

    private String buildPrompt(String message) {
        return """
                你是智能 DIY 装机助手的意图识别模块，只输出 JSON，不要输出 Markdown。
                可选 subIntent 只有：
                build_request, clarify_requirement, build_modify, build_compare,
                compatibility_check, risk_explain, preference_record, history_recall,
                recommendation_retry, chitchat。

                判断原则：
                预算、用途、屏幕分辨率、机箱大小等首次装机条件：build_request。
                在已有方案上替换、升级、降预算、对比：build_modify 或 build_compare。
                询问能不能装、兼容吗、能不能直接买：compatibility_check。
                询问 BIOS、电源、散热、尺寸等风险含义：risk_explain。
                表达长期品牌、尺寸、静音、外观偏好：preference_record。
                调用上次、历史、收藏：history_recall。
                重新推荐、再来一套：recommendation_retry。
                寒暄、感谢、结束：chitchat。

                输出格式：
                {"intent":"大类","subIntent":"子意图","confidence":0.0,"reasoning":"一句话原因","extractedData":{},"safetyCheck":{"required":true,"riskLevel":"WARN","reason":"一句话原因"}}
                extractedData 只填写用户明确提到的信息，不要臆造；没有就返回空对象。
                safetyCheck.required 表示是否需要进入第三层业务安全检查。
                safetyCheck.riskLevel 只能是 PASS、WARN、URGENT、BLOCK。

                用户消息：
                %s
                """.formatted(message);
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private IntentAnalysis fallbackAnalyze(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "兼容", "能装", "能买", "点亮", "冲突")) {
            return IntentAnalysis.fallback("compatibility_check", "命中兼容性关键词");
        }
        if (containsAny(text, "bios", "风险", "电源", "散热", "限高", "显卡长度")) {
            return IntentAnalysis.fallback("risk_explain", "命中风险解释关键词");
        }
        if (containsAny(text, "换", "升级", "降到", "加预算", "减预算", "对比")) {
            return IntentAnalysis.fallback("build_modify", "命中方案调整关键词");
        }
        if (containsAny(text, "预算", "游戏", "剪辑", "建模", "办公", "配置", "装机")) {
            return IntentAnalysis.fallback("build_request", "命中装机需求关键词");
        }
        if (containsAny(text, "不要", "偏好", "安静", "小机箱", "白色", "品牌")) {
            return IntentAnalysis.fallback("preference_record", "命中偏好关键词");
        }
        return IntentAnalysis.fallback("chitchat", "未命中明确装机意图");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean needsSafetyCheck(String subIntent) {
        return switch (subIntent) {
            case "build_request", "build_modify", "build_compare", "compatibility_check", "risk_explain", "recommendation_retry" -> true;
            default -> false;
        };
    }
}
