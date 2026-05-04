package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JdProductSearchServiceMatchingTests {

    private final JdProductSearchService service = new JdProductSearchService(
            mock(HardwarePartRepository.class),
            new HardwareModelMatcher(),
            new ObjectMapper()
    );

    @Test
    void rtx5070UltraDoesNotMatchRtx5070Ti() {
        HardwarePart rtx5070Ti = part(HardwarePartType.GPU, "RTX 5070 Ti", 91, "", "", "", 300, "");

        Boolean result = ReflectionTestUtils.invokeMethod(
                service,
                "matchesStandardModelForProduct",
                "七彩虹RTX5070 Ultra W OC 12G显卡",
                rtx5070Ti
        );

        assertFalse(Boolean.TRUE.equals(result));
    }

    @Test
    void rtx5070UltraStillMatchesPlainRtx5070() {
        HardwarePart rtx5070 = part(HardwarePartType.GPU, "RTX 5070", 86, "", "", "", 250, "");

        Boolean result = ReflectionTestUtils.invokeMethod(
                service,
                "matchesStandardModelForProduct",
                "七彩虹RTX5070 Ultra W OC 12G显卡",
                rtx5070
        );

        assertTrue(Boolean.TRUE.equals(result));
    }

    @Test
    void memoryProductMustMatchActualCapacity() {
        HardwarePart memory64 = part(HardwarePartType.MEMORY, "64GB DDR5 6400", 90, "", "DDR5", "", 0, "2x32GB 套条");

        Boolean result = ReflectionTestUtils.invokeMethod(
                service,
                "matchesMemoryIntent",
                "美商海盗船 VENGEANCE复仇者RGB灯条 台式机内存条DDR5 6400 16GB 单条",
                memory64
        );

        assertFalse(Boolean.TRUE.equals(result));
    }

    @Test
    void memoryKitCapacityCanMatchByPairNotation() {
        HardwarePart memory64 = part(HardwarePartType.MEMORY, "64GB DDR5 6400", 90, "", "DDR5", "", 0, "2x32GB 套条");

        Boolean result = ReflectionTestUtils.invokeMethod(
                service,
                "matchesMemoryIntent",
                "美商海盗船 VENGEANCE DDR5 6400 32Gx2 台式机内存套条",
                memory64
        );

        assertTrue(Boolean.TRUE.equals(result));
    }

    private HardwarePart part(
            HardwarePartType type,
            String model,
            int score,
            String socket,
            String memoryType,
            String formFactor,
            int wattage,
            String specs
    ) {
        HardwarePart part = new HardwarePart(type, model, 0, score);
        part.update(type, model, 0, score, socket, memoryType, formFactor, wattage, 0, 0, "", specs, true);
        return part;
    }
}
