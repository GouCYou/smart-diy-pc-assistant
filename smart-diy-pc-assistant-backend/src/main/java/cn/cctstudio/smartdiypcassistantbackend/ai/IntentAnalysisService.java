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
        AiSettingsService.EffectiveAiSettings settings = new AiSettingsService.EffectiveAiSettings(
                properties.baseUrl(),
                properties.apiKey(),
                properties.modelName(),
                properties.timeoutSeconds(),
                12,
                180,
                420,
                AiBuildTriggerMode.SENSITIVE,
                true,
                true,
                AiColorMatchMode.PREFER,
                ""
        );
        return analyze(message, false, settings);
    }

    public IntentAnalysis analyze(String message, boolean hasAttachedBuild, AiSettingsService.EffectiveAiSettings settings) {
        if (settings == null || !StringUtils.hasText(settings.apiKey()) || !StringUtils.hasText(settings.modelName())) {
            return fallbackAnalyze(message, hasAttachedBuild);
        }
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .apiKey(settings.apiKey())
                    .baseUrl(settings.baseUrl())
                    .modelName(settings.modelName())
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .build();
            String raw = model.generate(buildPrompt(message, hasAttachedBuild));
            JsonNode json = objectMapper.readTree(extractJson(raw));
            String subIntent = json.path("subIntent").asText("chitchat");
            JsonNode toolNode = json.path("toolDecision");
            AiToolAction toolAction = AiToolAction.from(toolNode.path("action").asText(""));
            return new IntentAnalysis(
                    json.path("intent").asText("普通对话"),
                    subIntent,
                    json.path("confidence").asDouble(0.7),
                    json.path("reasoning").asText("已完成意图识别"),
                    json.path("extractedData").isMissingNode() ? "{}" : json.path("extractedData").toString(),
                    json.path("safetyCheck").path("required").asBoolean(needsSafetyCheck(subIntent)),
                    json.path("safetyCheck").path("riskLevel").asText(needsSafetyCheck(subIntent) ? "WARN" : "PASS"),
                    toolAction,
                    toolNode.path("confidence").asDouble(json.path("confidence").asDouble(0.7)),
                    toolNode.path("reason").asText(json.path("reasoning").asText("已完成工具路由")),
                    toolNode.path("args").isMissingNode() ? "{}" : toolNode.path("args").toString()
            );
        } catch (RuntimeException exception) {
            return fallbackAnalyze(message, hasAttachedBuild);
        } catch (Exception exception) {
            return fallbackAnalyze(message, hasAttachedBuild);
        }
    }

    private String buildPrompt(String message, boolean hasAttachedBuild) {
        return """
                你是智能 DIY 装机助手的意图和工具路由模块，只输出 JSON，不要输出 Markdown。
                可选 subIntent 只有：
                build_request, clarify_requirement, build_modify, build_compare,
                compatibility_check, risk_explain, preference_record, history_recall,
                recommendation_retry, chitchat。
                可选 toolDecision.action 只有：
                DIRECT_ANSWER, GENERATE_BUILD, MODIFY_BUILD, REVIEW_BUILD, SUMMARIZE_NEWS, ASK_CLARIFICATION。

                判断原则：
                预算、用途、屏幕分辨率、机箱大小等首次装机条件，并且用户明确要“生成/保存/配一套/出一台”时：build_request + GENERATE_BUILD。
                在已有方案上替换、升级、降预算、对比：build_modify 或 build_compare。
                询问能不能装、兼容吗、能不能直接买：compatibility_check。
                询问 BIOS、电源、散热、尺寸等风险含义：risk_explain。
                表达长期品牌、尺寸、静音、外观偏好：preference_record。
                调用上次、历史、收藏：history_recall。
                重新推荐、再来一套：recommendation_retry。
                寒暄、感谢、结束：chitchat。
                询问新闻、热点、最近、今天、发布会、资讯，并且目标是了解硬件动态：SUMMARIZE_NEWS。
                已附带配置单时，询问“这套怎么样/哪里不合理/评价一下”：REVIEW_BUILD。
                询问山寨电源、杂牌电源、二手风险、BIOS 风险、散热风险、是否能用等问题：DIRECT_ANSWER，不要选择 GENERATE_BUILD。
                用户只是在问概念、风险、能不能用、为什么、怎么选：DIRECT_ANSWER。
                用户没有预算、用途或明确生成动作，但想装机：ASK_CLARIFICATION。

                输出格式：
                {"intent":"大类","subIntent":"子意图","confidence":0.0,"reasoning":"一句话原因","extractedData":{},"safetyCheck":{"required":true,"riskLevel":"WARN","reason":"一句话原因"},"toolDecision":{"action":"DIRECT_ANSWER","confidence":0.0,"reason":"一句话原因","args":{}}}
                extractedData 只填写用户明确提到的信息，不要臆造；没有就返回空对象。
                safetyCheck.required 表示是否需要进入第三层业务安全检查。
                safetyCheck.riskLevel 只能是 PASS、WARN、URGENT、BLOCK。
                toolDecision.args 只填写工具执行必要参数；没有就返回空对象。

                是否附带配置单：%s
                用户消息：
                %s
                """.formatted(hasAttachedBuild ? "是" : "否", message);
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private IntentAnalysis fallbackAnalyze(String message, boolean hasAttachedBuild) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "新闻", "热点", "资讯", "报道", "爆料", "发布会", "原文")
                || (containsAny(text, "最近", "最新", "今天", "近期", "这几天")
                && containsAny(text, "显卡", "cpu", "处理器", "硬件", "电脑", "装机", "英伟达", "nvidia", "amd", "intel", "英特尔", "锐龙", "酷睿", "主板", "内存", "固态"))) {
            return IntentAnalysis.fallback("chitchat", "命中硬件新闻或热点关键词")
                    .withToolAction(AiToolAction.SUMMARIZE_NEWS, 0.68, "需要引用站内热点新闻上下文回答");
        }
        if (containsAny(text, "兼容", "能装", "能买", "点亮", "冲突")) {
            return IntentAnalysis.fallback("compatibility_check", "命中兼容性关键词")
                    .withToolAction(AiToolAction.DIRECT_ANSWER, 0.72, "这是兼容性问答，不应直接生成配置单");
        }
        if (containsAny(text, "bios", "风险", "电源", "散热", "限高", "显卡长度", "山寨", "杂牌", "三无", "劣质")) {
            return IntentAnalysis.fallback("risk_explain", "命中风险解释关键词")
                    .withToolAction(AiToolAction.DIRECT_ANSWER, 0.76, "这是硬件风险解释，不应直接生成配置单");
        }
        if (containsAny(text, "换", "升级", "降到", "加预算", "减预算", "对比")) {
            return IntentAnalysis.fallback("build_modify", "命中方案调整关键词")
                    .withToolAction(hasAttachedBuild ? AiToolAction.MODIFY_BUILD : AiToolAction.DIRECT_ANSWER, 0.64, hasAttachedBuild ? "已有配置单，可尝试调整方案" : "没有附带配置单，先口头说明或追问");
        }
        if (containsAny(text, "生成配置", "生成一套", "保存配置", "配一套", "组一套", "攒一套", "出一套", "出一台", "来一套", "来一台", "帮我配", "帮我组", "配电脑", "组电脑")
                || (containsAny(text, "预算") && containsAny(text, "游戏", "剪辑", "建模", "办公", "配置", "装机", "电脑", "主机"))) {
            return IntentAnalysis.fallback("build_request", "命中明确装机生成需求")
                    .withToolAction(AiToolAction.GENERATE_BUILD, 0.7, "用户明确要求生成配置单");
        }
        if (hasAttachedBuild && containsAny(text, "这套", "配置", "怎么样", "评价", "合理", "问题", "优化")) {
            return IntentAnalysis.fallback("compatibility_check", "命中配置评价关键词")
                    .withToolAction(AiToolAction.REVIEW_BUILD, 0.66, "用户在询问已附带配置单");
        }
        if (containsAny(text, "不要", "偏好", "安静", "小机箱", "白色", "品牌")) {
            return IntentAnalysis.fallback("preference_record", "命中偏好关键词")
                    .withToolAction(AiToolAction.DIRECT_ANSWER, 0.62, "偏好记录或普通问答");
        }
        return IntentAnalysis.fallback("chitchat", "未命中明确装机意图")
                .withToolAction(AiToolAction.DIRECT_ANSWER, 0.62, "直接口头回答");
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
