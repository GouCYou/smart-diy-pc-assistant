package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.config.properties.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentAnalysisRoutingTests {

    private final IntentAnalysisService service = new IntentAnalysisService(
            new AiProperties("", "", "", 60),
            new ObjectMapper()
    );

    private final AiSettingsService.EffectiveAiSettings settings = new AiSettingsService.EffectiveAiSettings(
            "",
            "",
            "",
            60,
            12,
            180,
            420,
            AiBuildTriggerMode.SENSITIVE,
            true,
            true,
            AiColorMatchMode.PREFER,
            ""
    );

    @Test
    void riskQuestionRoutesToDirectAnswer() {
        IntentAnalysis analysis = service.analyze("我可以去用山寨电源装机吗？", false, settings);

        assertEquals("risk_explain", analysis.subIntent());
        assertEquals(AiToolAction.DIRECT_ANSWER, analysis.toolAction());
    }

    @Test
    void explicitBuildRequestRoutesToGenerateBuild() {
        IntentAnalysis analysis = service.analyze("预算 13500 元，主玩游戏，请帮我生成一套配置单。", false, settings);

        assertEquals("build_request", analysis.subIntent());
        assertEquals(AiToolAction.GENERATE_BUILD, analysis.toolAction());
    }

    @Test
    void hardwareNewsRoutesToNewsTool() {
        IntentAnalysis analysis = service.analyze("最近显卡有什么热点？", false, settings);

        assertEquals(AiToolAction.SUMMARIZE_NEWS, analysis.toolAction());
    }
}
