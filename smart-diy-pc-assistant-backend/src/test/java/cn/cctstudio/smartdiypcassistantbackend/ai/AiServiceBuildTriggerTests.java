package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigSource;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AiServiceBuildTriggerTests {

    private final AiService service = new AiService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            directExecutor(),
            mock(PlatformTransactionManager.class)
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
    void riskQuestionDoesNotStartBuildGeneration() {
        assertFalse(shouldGenerate("我可以去用山寨电源装机吗？"));
        assertFalse(shouldGenerate("台式机可以用山寨电源吗？"));
        assertFalse(shouldGenerate("这个主机配置能不能直接买？"));
        assertFalse(shouldGenerate("这个电源能不能装机？"));
        assertFalse(shouldGenerate("装机需要注意什么"));
    }

    @Test
    void explicitBuildRequestStillStartsGeneration() {
        assertTrue(shouldGenerate("预算 13500 元，主要用于游戏，请帮我生成一套配置单。"));
        assertTrue(shouldGenerate("帮我配电脑，预算 8000，主玩游戏。"));
    }

    @Test
    void generatedReplyDoesNotInventFpsOrAppearanceWithoutSpecificRequest() {
        BuildConfigResponse build = buildResponse("根据你的需求自动生成。需求：预算 14500 元，主要用于游戏，请生成一套配置单。");

        String reply = ReflectionTestUtils.invokeMethod(service, "createGeneratedBuildReply", false, build);

        assertFalse(reply.contains("帧率预估"));
        assertFalse(reply.contains("外观说明"));
        assertFalse(reply.contains("配色"));
    }

    @Test
    void generatedReplyKeepsFpsAdviceForNamedGamesOnly() {
        BuildConfigResponse build = buildResponse("根据你的需求自动生成。需求：预算 14500 元，主要用于 CS2，目标 240 帧。");

        String reply = ReflectionTestUtils.invokeMethod(service, "createGeneratedBuildReply", false, build);

        assertTrue(reply.contains("帧率预估"));
        assertTrue(reply.contains("CS2"));
    }

    private boolean shouldGenerate(String message) {
        Boolean result = ReflectionTestUtils.invokeMethod(
                service,
                "shouldGenerateBuild",
                message,
                false,
                settings
        );
        return Boolean.TRUE.equals(result);
    }

    private BuildConfigResponse buildResponse(String note) {
        return new BuildConfigResponse(
                1L,
                "AI-0001",
                "AI 生成 游戏配置",
                note,
                14500,
                92,
                "OK",
                List.of(),
                List.of(
                        part(1L, HardwarePartType.CPU, "Ryzen 7 9800X3D", 98),
                        part(2L, HardwarePartType.GPU, "RTX 5070", 86),
                        part(3L, HardwarePartType.MOTHERBOARD, "B850M", 0),
                        part(4L, HardwarePartType.MEMORY, "32GB DDR5 6000", 82),
                        part(5L, HardwarePartType.PSU, "750W ATX 3.1 Gold PSU", 0)
                ),
                "",
                "",
                BuildConfigSource.AI,
                false,
                "",
                null,
                LocalDateTime.now()
        );
    }

    private HardwarePartResponse part(Long id, HardwarePartType type, String model, int score) {
        return new HardwarePartResponse(
                id,
                type,
                "",
                model,
                0,
                "",
                0,
                "",
                "",
                "",
                model,
                score,
                "",
                type == HardwarePartType.MEMORY ? "DDR5" : "",
                "",
                0,
                0,
                0,
                "",
                "",
                true
        );
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }
}
