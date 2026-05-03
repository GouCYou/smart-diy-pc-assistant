package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentAndSafetyRuleTests {

    @Test
    void counterfeitPsuGetsDirectRiskAnswer() {
        IntentRuleService service = new IntentRuleService();

        IntentRuleService.IntentMatch match = service.match("我可以去用山寨电源装机吗？").orElseThrow();

        assertEquals("risk_explain", match.intent());
        assertTrue(match.reply().contains("不建议"));
        assertTrue(match.reply().contains("电源"));
    }

    @Test
    void counterfeitPsuIsUrgentHardwareRisk() {
        SafetyResult result = new SafetyRuleService().check("我可以去用山寨电源装机吗？");

        assertEquals(SafetyLevel.URGENT, result.level());
        assertTrue(result.message().contains("山寨"));
    }
}
