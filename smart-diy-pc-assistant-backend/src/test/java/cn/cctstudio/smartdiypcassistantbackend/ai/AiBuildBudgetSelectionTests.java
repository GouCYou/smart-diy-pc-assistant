package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.commerce.JdProduct;
import cn.cctstudio.smartdiypcassistantbackend.commerce.JdProductSearchService;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiBuildBudgetSelectionTests {

    private final HardwarePartRepository hardwarePartRepository = mock(HardwarePartRepository.class);
    private final JdProductSearchService jdProductSearchService = mock(JdProductSearchService.class);
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
            hardwarePartRepository,
            null,
            null,
            jdProductSearchService,
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
    void explicitBudgetsStayInTheirExpectedTiersWhenProductsAreAvailable() {
        List<HardwarePart> parts = seededHardware();
        when(hardwarePartRepository.findByEnabledTrue()).thenReturn(parts);
        when(jdProductSearchService.availableProductsForPart(
                any(HardwarePart.class),
                anyString(),
                anyBoolean(),
                anyString(),
                anyString()
        )).thenAnswer(invocation -> {
            HardwarePart part = invocation.getArgument(0);
            return List.of(productFor(part));
        });

        assertBudgetTier(6000, 4200, 6600);
        assertBudgetTier(8000, 6800, 9200);
        assertBudgetTier(10000, 8200, 11200);
        assertBudgetTier(14500, 11890, 15700);
        assertBudgetTier(18000, 15300, 19200);
    }

    @Test
    void gamingBuildPrefersX3dCpuAndNvidiaGpuWhenBudgetAllows() {
        List<HardwarePart> parts = seededHardware();
        when(hardwarePartRepository.findByEnabledTrue()).thenReturn(parts);
        when(jdProductSearchService.availableProductsForPart(
                any(HardwarePart.class),
                anyString(),
                anyBoolean(),
                anyString(),
                anyString()
        )).thenAnswer(invocation -> List.of(productFor(invocation.getArgument(0))));

        Object plan = ReflectionTestUtils.invokeMethod(
                service,
                "selectBuildPlan",
                14500,
                "预算 14500 元，主要用于游戏，品牌不强制，显卡平台不强制，请生成一套配置单。",
                settings,
                (UserMemory) null
        );

        String cpu = partModel(plan, HardwarePartType.CPU).toUpperCase();
        String gpu = partModel(plan, HardwarePartType.GPU).toUpperCase();
        assertTrue(cpu.contains("X3D"), () -> "游戏预算充足时应优先 X3D CPU，实际选择 " + cpu);
        assertFalse(cpu.contains("9900X") && !cpu.contains("X3D"), () -> "游戏配置不应默认选择非 X3D 的 9900X，实际选择 " + cpu);
        assertTrue(gpu.startsWith("RTX"), () -> "显卡平台未指定且预算充足时应默认偏向 NVIDIA，实际选择 " + gpu);
    }

    @Test
    void cpuMotherboardBundleCanReplaceTheSelectedBoard() {
        List<HardwarePart> parts = seededHardware();
        when(hardwarePartRepository.findByEnabledTrue()).thenReturn(parts);
        HardwarePart cpu = findPart(parts, HardwarePartType.CPU, "Ryzen 7 9800X3D");
        HardwarePart selectedBoard = findPart(parts, HardwarePartType.MOTHERBOARD, "B850");
        HardwarePart memory = findPart(parts, HardwarePartType.MEMORY, "32GB DDR5 6000");
        HardwarePart pcCase = findPart(parts, HardwarePartType.CASE, "ATX Airflow Case");
        List<HardwarePart> candidateParts = new ArrayList<>(List.of(cpu, selectedBoard, memory, pcCase));

        @SuppressWarnings("unchecked")
        java.util.Optional<HardwarePart> resolved = ReflectionTestUtils.invokeMethod(
                service,
                "resolveBundledMotherboard",
                "七彩虹 B650M/X870/B850M 主板搭 AMD 锐龙 R7 9800X3D CPU主板套装 B850M-E WIFI V14【战斧】",
                cpu,
                candidateParts
        );

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().getModel().contains("B850M"));
    }

    private void assertBudgetTier(int budget, int minimum, int maximum) {
        Object plan = ReflectionTestUtils.invokeMethod(
                service,
                "selectBuildPlan",
                budget,
                "预算 " + budget + " 元，主要用于游戏，优先性价比和稳定性，请生成一套配置单。",
                settings,
                (UserMemory) null
        );
        int total = intRecordValue(plan, "totalPrice");
        assertTrue(total >= minimum,
                () -> "预算 " + budget + " 不应下探到低档配置，实际总价 " + total + "，期望至少 " + minimum);
        assertTrue(total <= maximum,
                () -> "预算 " + budget + " 不应明显超预算，实际总价 " + total + "，期望不超过 " + maximum);
    }

    private JdProduct productFor(HardwarePart part) {
        return new JdProduct(
                part.getModel() + " 京东自营 全新 " + typeLabel(part.getType()),
                fixturePrice(part),
                "https://img.example.com/" + part.getId() + ".jpg",
                "https://item.jd.com/" + part.getId() + ".html",
                String.valueOf(1_000_000 + part.getId()),
                "京东自营",
                part.getModel(),
                part.getId(),
                part.getType(),
                part.getPerformanceScore(),
                part.getSocketType(),
                part.getMemoryType(),
                part.getFormFactor(),
                part.getWattage(),
                part.getGpuLengthMm(),
                part.getCoolerHeightMm(),
                part.getSupportedFormFactors(),
                part.getSpecs()
        );
    }

    private int fixturePrice(HardwarePart part) {
        String model = part.getModel().toUpperCase();
        return switch (part.getType()) {
            case CPU -> {
                if (model.contains("9950X3D2")) yield 6500;
                if (model.contains("9950X3D")) yield 5200;
                if (model.contains("9950X")) yield 4300;
                if (model.contains("9900X3D")) yield 3800;
                if (model.contains("9900X")) yield 3200;
                if (model.contains("9850X3D")) yield 3600;
                if (model.contains("9800X3D")) yield 3400;
                if (model.contains("9700X")) yield 1900;
                if (model.contains("9600X")) yield 1400;
                if (model.contains("7800X3D")) yield 2500;
                if (model.contains("7500F")) yield 900;
                if (model.contains("5700X3D")) yield 1000;
                if (model.contains("5600")) yield 600;
                if (model.contains("CORE ULTRA 9")) yield 4200;
                if (model.contains("CORE ULTRA 7")) yield 2800;
                if (model.contains("CORE ULTRA 5")) yield 1800;
                if (model.contains("14700")) yield 2400;
                if (model.contains("14600")) yield 1500;
                if (model.contains("13490")) yield 1100;
                if (model.contains("12400")) yield 750;
                yield 1500;
            }
            case GPU -> {
                if (model.contains("RTX 5090")) yield 22000;
                if (model.contains("RTX 5080")) yield 9000;
                if (model.contains("RTX 5070 TI")) yield 6200;
                if (model.contains("RTX 5070")) yield 4800;
                if (model.contains("RTX 5060 TI")) yield 3000;
                if (model.contains("RTX 5060")) yield 2300;
                if (model.contains("RTX 5050")) yield 1600;
                if (model.contains("RTX 4070 SUPER")) yield 4300;
                if (model.contains("RTX 4070")) yield 3400;
                if (model.contains("RTX 4060 TI")) yield 2600;
                if (model.contains("RTX 4060")) yield 2100;
                if (model.contains("RX 9070 XT")) yield 5200;
                if (model.contains("RX 9070")) yield 4300;
                if (model.contains("RX 9060 XT")) yield 2600;
                if (model.contains("RX 7800 XT")) yield 3500;
                if (model.contains("RX 7700 XT")) yield 2900;
                yield 2500;
            }
            case MOTHERBOARD -> {
                if (model.contains("X870E")) yield 2500;
                if (model.contains("X870")) yield 1800;
                if (model.contains("B850M")) yield 900;
                if (model.contains("B850")) yield 1100;
                if (model.contains("B650E")) yield 1200;
                if (model.contains("B650M")) yield 700;
                if (model.contains("B650")) yield 900;
                if (model.contains("Z890")) yield 2000;
                if (model.contains("B860M")) yield 800;
                if (model.contains("B860")) yield 950;
                if (model.contains("H810")) yield 550;
                if (model.contains("Z790")) yield 1600;
                if (model.contains("B760M")) yield 700;
                if (model.contains("B760")) yield 850;
                if (model.contains("B660M")) yield 550;
                if (model.contains("B550M")) yield 550;
                yield 800;
            }
            case MEMORY -> {
                if (model.contains("64GB") && model.contains("DDR5")) yield 1800;
                if (model.contains("64GB")) yield 1500;
                if (model.contains("32GB") && model.contains("DDR5") && model.contains("7200")) yield 1300;
                if (model.contains("32GB") && model.contains("DDR5") && model.contains("6400")) yield 1100;
                if (model.contains("32GB") && model.contains("DDR5")) yield 950;
                if (model.contains("16GB") && model.contains("DDR5")) yield 520;
                if (model.contains("32GB") && model.contains("DDR4")) yield 650;
                if (model.contains("16GB") && model.contains("DDR4")) yield 380;
                yield 800;
            }
            case STORAGE -> {
                if (model.contains("PCIE 5.0") && model.contains("2TB")) yield 2200;
                if (model.contains("PCIE 5.0") && model.contains("1TB")) yield 1050;
                if (model.contains("4TB")) yield 3000;
                if (model.contains("2TB")) yield 1350;
                if (model.contains("1TB")) yield 700;
                yield 900;
            }
            case PSU -> {
                if (model.contains("1000W")) yield 900;
                if (model.contains("850W") && model.contains("ATX 3.1")) yield 650;
                if (model.contains("750W") && model.contains("ATX 3.1")) yield 520;
                if (model.contains("650W") && model.contains("ATX 3.1")) yield 420;
                if (model.contains("850W")) yield 580;
                if (model.contains("750W")) yield 450;
                if (model.contains("650W") && model.contains("BRONZE")) yield 280;
                if (model.contains("650W")) yield 360;
                yield 400;
            }
            case CASE -> model.contains("E-ATX") ? 700 : model.contains("ITX") ? 450 : model.contains("M-ATX") ? 300 : 350;
            case COOLER -> model.contains("360") ? 520 : model.contains("280") ? 430 : model.contains("240") ? 300
                    : model.contains("DUAL TOWER") ? 190 : model.contains("LOW PROFILE") ? 160 : 100;
        };
    }

    private List<HardwarePart> seededHardware() {
        List<HardwarePart> parts = new ArrayList<>();
        long id = 1L;
        parts.add(cpu(id++, "Ryzen 9 9950X3D2", 100, "AM5", 170));
        parts.add(cpu(id++, "Ryzen 9 9950X3D", 99, "AM5", 170));
        parts.add(cpu(id++, "Ryzen 9 9900X3D", 97, "AM5", 120));
        parts.add(cpu(id++, "Ryzen 7 9850X3D", 99, "AM5", 120));
        parts.add(cpu(id++, "Ryzen 7 9800X3D", 98, "AM5", 120));
        parts.add(cpu(id++, "Ryzen 9 9950X", 96, "AM5", 170));
        parts.add(cpu(id++, "Ryzen 9 9900X", 93, "AM5", 120));
        parts.add(cpu(id++, "Ryzen 7 9700X", 88, "AM5", 65));
        parts.add(cpu(id++, "Ryzen 5 9600X", 82, "AM5", 65));
        parts.add(cpu(id++, "Core Ultra 9 285K", 94, "LGA1851", 125));
        parts.add(cpu(id++, "Core Ultra 7 270K Plus", 93, "LGA1851", 125));
        parts.add(cpu(id++, "Core Ultra 7 265K", 90, "LGA1851", 125));
        parts.add(cpu(id++, "Core Ultra 5 250K Plus", 84, "LGA1851", 125));
        parts.add(cpu(id++, "Core Ultra 5 245K", 80, "LGA1851", 125));
        parts.add(cpu(id++, "Ryzen 7 7800X3D", 94, "AM5", 120));
        parts.add(cpu(id++, "Ryzen 5 7500F", 76, "AM5", 65));
        parts.add(cpu(id++, "Core i7-14700KF", 86, "LGA1700", 125));
        parts.add(cpu(id++, "Core i5-14600KF", 80, "LGA1700", 125));
        parts.add(cpu(id++, "Ryzen 7 5700X3D", 78, "AM4", 105));
        parts.add(cpu(id++, "Ryzen 5 5600", 62, "AM4", 65));

        parts.add(motherboard(id++, "X870E", "AM5", "DDR5", "ATX"));
        parts.add(motherboard(id++, "X870", "AM5", "DDR5", "ATX"));
        parts.add(motherboard(id++, "B850M", "AM5", "DDR5", "M-ATX"));
        parts.add(motherboard(id++, "B850", "AM5", "DDR5", "ATX"));
        parts.add(motherboard(id++, "B650E", "AM5", "DDR5", "ATX"));
        parts.add(motherboard(id++, "B650M", "AM5", "DDR5", "M-ATX"));
        parts.add(motherboard(id++, "B650", "AM5", "DDR5", "ATX"));
        parts.add(motherboard(id++, "Z890", "LGA1851", "DDR5", "ATX"));
        parts.add(motherboard(id++, "B860M", "LGA1851", "DDR5", "M-ATX"));
        parts.add(motherboard(id++, "B860", "LGA1851", "DDR5", "ATX"));
        parts.add(motherboard(id++, "H810", "LGA1851", "DDR5", "M-ATX"));
        parts.add(motherboard(id++, "Z790", "LGA1700", "DDR5", "ATX"));
        parts.add(motherboard(id++, "B760M DDR5", "LGA1700", "DDR5", "M-ATX"));
        parts.add(motherboard(id++, "B760 DDR4", "LGA1700", "DDR4", "ATX"));
        parts.add(motherboard(id++, "B660M D4", "LGA1700", "DDR4", "M-ATX"));
        parts.add(motherboard(id++, "B550M", "AM4", "DDR4", "M-ATX"));

        parts.add(gpu(id++, "RTX 5090", 100, 575));
        parts.add(gpu(id++, "RTX 5080", 96, 360));
        parts.add(gpu(id++, "RTX 5070 Ti", 91, 300));
        parts.add(gpu(id++, "RTX 5070", 86, 250));
        parts.add(gpu(id++, "RTX 5060 Ti", 78, 180));
        parts.add(gpu(id++, "RTX 5060", 72, 145));
        parts.add(gpu(id++, "RTX 5050", 64, 130));
        parts.add(gpu(id++, "RX 9070 XT", 90, 304));
        parts.add(gpu(id++, "RX 9070", 86, 220));
        parts.add(gpu(id++, "RX 9060 XT", 76, 160));
        parts.add(gpu(id++, "RTX 4070 SUPER", 84, 220));
        parts.add(gpu(id++, "RTX 4070", 80, 200));
        parts.add(gpu(id++, "RTX 4060 Ti", 70, 160));
        parts.add(gpu(id++, "RTX 4060", 64, 115));
        parts.add(gpu(id++, "RX 7800 XT", 80, 263));
        parts.add(gpu(id++, "RX 7700 XT", 74, 245));

        parts.add(memory(id++, "64GB DDR5 6400", 90, "DDR5"));
        parts.add(memory(id++, "32GB DDR5 7200", 88, "DDR5"));
        parts.add(memory(id++, "32GB DDR5 6400", 84, "DDR5"));
        parts.add(memory(id++, "32GB DDR5 6000", 82, "DDR5"));
        parts.add(memory(id++, "16GB DDR5 6000", 70, "DDR5"));
        parts.add(memory(id++, "32GB DDR4 3600", 64, "DDR4"));
        parts.add(memory(id++, "16GB DDR4 3200", 56, "DDR4"));

        parts.add(storage(id++, "PCIe 5.0 NVMe SSD 2TB", 94));
        parts.add(storage(id++, "PCIe 5.0 NVMe SSD 1TB", 88));
        parts.add(storage(id++, "PCIe 4.0 NVMe SSD 4TB", 88));
        parts.add(storage(id++, "PCIe 4.0 NVMe SSD 2TB", 84));
        parts.add(storage(id++, "PCIe 4.0 NVMe SSD 1TB", 76));

        parts.add(psu(id++, "1000W ATX 3.1 Gold PSU", 1000));
        parts.add(psu(id++, "850W ATX 3.1 Gold PSU", 850));
        parts.add(psu(id++, "750W ATX 3.1 Gold PSU", 750));
        parts.add(psu(id++, "650W ATX 3.1 Gold PSU", 650));
        parts.add(psu(id++, "850W Gold PSU", 850));
        parts.add(psu(id++, "750W Gold PSU", 750));
        parts.add(psu(id++, "650W Gold PSU", 650));
        parts.add(psu(id++, "650W Bronze PSU", 650));

        parts.add(pcCase(id++, "E-ATX Airflow Case", "E-ATX", "E-ATX,ATX,M-ATX,ITX"));
        parts.add(pcCase(id++, "ATX Airflow Case", "ATX", "ATX,M-ATX,ITX"));
        parts.add(pcCase(id++, "M-ATX Mesh Case", "M-ATX", "M-ATX,ITX"));
        parts.add(pcCase(id++, "ITX Compact Case", "ITX", "ITX"));

        parts.add(cooler(id++, "360mm AIO Cooler"));
        parts.add(cooler(id++, "280mm AIO Cooler"));
        parts.add(cooler(id++, "240mm AIO Cooler"));
        parts.add(cooler(id++, "Dual Tower Cooler"));
        parts.add(cooler(id++, "120mm Tower Cooler"));
        parts.add(cooler(id++, "Low Profile Cooler"));
        return parts;
    }

    private HardwarePart findPart(List<HardwarePart> parts, HardwarePartType type, String model) {
        return parts.stream()
                .filter(part -> part.getType() == type && part.getModel().equals(model))
                .findFirst()
                .orElseThrow();
    }

    private HardwarePart cpu(long id, String model, int score, String socket, int wattage) {
        return part(id, HardwarePartType.CPU, model, score, socket, "", "", wattage, "");
    }

    private HardwarePart motherboard(long id, String model, String socket, String memoryType, String formFactor) {
        return part(id, HardwarePartType.MOTHERBOARD, model, 0, socket, memoryType, formFactor, 0, "");
    }

    private HardwarePart gpu(long id, String model, int score, int wattage) {
        return part(id, HardwarePartType.GPU, model, score, "", "", "", wattage, "");
    }

    private HardwarePart memory(long id, String model, int score, String memoryType) {
        return part(id, HardwarePartType.MEMORY, model, score, "", memoryType, "", 0, "2x16GB 套条");
    }

    private HardwarePart storage(long id, String model, int score) {
        return part(id, HardwarePartType.STORAGE, model, score, "", "", "", 0, "NVMe M.2 固态硬盘");
    }

    private HardwarePart psu(long id, String model, int wattage) {
        return part(id, HardwarePartType.PSU, model, 0, "", "", "", wattage, "");
    }

    private HardwarePart pcCase(long id, String model, String formFactor, String supportedFormFactors) {
        return part(id, HardwarePartType.CASE, model, 0, "", "", formFactor, 0, supportedFormFactors);
    }

    private HardwarePart cooler(long id, String model) {
        return part(id, HardwarePartType.COOLER, model, 0, "", "", "", 0, "");
    }

    private HardwarePart part(
            long id,
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
        String supportedFormFactors = type == HardwarePartType.CASE ? specs : "";
        String actualSpecs = type == HardwarePartType.CASE ? "" : specs;
        part.update(type, model, 0, score, socket, memoryType, formFactor, wattage, 0, 0, supportedFormFactors, actualSpecs, true);
        ReflectionTestUtils.setField(part, "id", id);
        return part;
    }

    private String typeLabel(HardwarePartType type) {
        return switch (type) {
            case CPU -> "CPU";
            case MOTHERBOARD -> "主板";
            case GPU -> "显卡";
            case MEMORY -> "内存";
            case STORAGE -> "固态硬盘";
            case PSU -> "电源";
            case CASE -> "机箱";
            case COOLER -> "散热器";
        };
    }

    private int intRecordValue(Object record, String methodName) {
        return recordValue(record, methodName);
    }

    @SuppressWarnings("unchecked")
    private String partModel(Object plan, HardwarePartType type) {
        List<HardwarePart> parts = recordValue(plan, "parts");
        return parts.stream()
                .filter(part -> part.getType() == type)
                .map(HardwarePart::getModel)
                .findFirst()
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private <T> T recordValue(Object record, String methodName) {
        try {
            Method method = record.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (T) method.invoke(record);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to read " + methodName, exception);
        }
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }
}
