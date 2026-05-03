package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import cn.cctstudio.smartdiypcassistantbackend.ai.AiSettingsService;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
public class BuildConfigAiReviewService {

    private final BuildConfigRepository buildConfigRepository;
    private final BuildConfigAiReviewRepository buildConfigAiReviewRepository;
    private final BuildConfigService buildConfigService;
    private final AiSettingsService aiSettingsService;

    public BuildConfigAiReviewService(
            BuildConfigRepository buildConfigRepository,
            BuildConfigAiReviewRepository buildConfigAiReviewRepository,
            BuildConfigService buildConfigService,
            AiSettingsService aiSettingsService
    ) {
        this.buildConfigRepository = buildConfigRepository;
        this.buildConfigAiReviewRepository = buildConfigAiReviewRepository;
        this.buildConfigService = buildConfigService;
        this.aiSettingsService = aiSettingsService;
    }

    @Transactional
    public BuildConfigResponse generate(Long userId, Long id) {
        BuildConfig config = buildConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException("配置不存在"));
        BuildConfigResponse build = buildConfigService.getPublic(userId, id);
        AiSettingsService.EffectiveAiSettings settings = aiSettingsService.effective();
        if (!StringUtils.hasText(settings.apiKey())) {
            throw new BusinessException("AI 服务尚未配置密钥，请稍后再试");
        }
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(settings.apiKey())
                .baseUrl(settings.baseUrl())
                .modelName(settings.modelName())
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();
        String review;
        try {
            review = model.generate(buildReviewPrompt(build));
        } catch (RuntimeException exception) {
            throw new BusinessException("AI 评价生成失败，请稍后再试");
        }
        if (!StringUtils.hasText(review)) {
            throw new BusinessException("AI 评价生成失败，请稍后再试");
        }
        BuildConfigAiReview reviewRecord = buildConfigAiReviewRepository
                .findByBuildConfigIdAndUserId(config.getId(), userId)
                .orElse(new BuildConfigAiReview(config.getId(), userId, ""));
        reviewRecord.updateReview(cleanReview(review));
        buildConfigAiReviewRepository.save(reviewRecord);
        return buildConfigService.getPublic(userId, id);
    }

    private String buildReviewPrompt(BuildConfigResponse build) {
        return """
                你是资深 DIY 电脑装机顾问。请基于下面这套已保存的配置单，用简体中文给出 AI评价。
                当前日期：%s。
                要求：
                1. 不使用 Markdown、表格、星号、井号或代码块。
                2. 先给一句结论，再给 4 到 6 条短评价。
                3. 必须结合配置备注里的原始预算和用途评价预算使用；如果实际总价明显低于备注预算，要指出预算利用不足，不能说成预算分配合理。
                4. 必须覆盖核心性能、兼容性、散热和电源余量、后续升级空间。
                5. 如果配置中存在板U套装、缺少京东链接、无价格或不可判断库存，要直接提醒。
                6. 不要编造未给出的商品、价格或链接；所有商品价格以配置单里的京东快照为准，不要用训练知识里的旧低价做市场价锚点。
                7. 内存和硬盘价格容易随行情波动；如果内存或固态看起来偏贵，用自然语言说明近期同类配件行情偏高、购买前按同规格再比价，不要简单判定搭配错误。
                8. 不要用旧知识否定 RTX 50 系、RX 9000 系或配置单里已经出现京东链接/SKU 的商品；不要输出“尚未正式发布”“未上市”“型号存在不确定性”“链接可能无效”这类判断。如需提醒，只能说以当前京东页面库存和价格为准。
                9. 控制在 260 到 420 个汉字之间，语气专业但直白。

                配置标题：%s
                配置备注：%s
                总价：¥%d
                综合性能：%d 分
                兼容性：%s
                兼容性提示：%s
                配件：
                %s
                """.formatted(
                LocalDate.now(),
                build.title(),
                StringUtils.hasText(build.note()) ? build.note() : "无",
                build.totalPrice(),
                build.performanceScore(),
                build.compatibilityStatus(),
                build.compatibilityMessages().isEmpty() ? "无" : String.join("；", build.compatibilityMessages()),
                build.parts().stream().map(this::formatPart).collect(Collectors.joining("\n"))
        );
    }

    private String formatPart(HardwarePartResponse part) {
        String productTitle = StringUtils.hasText(part.productTitle()) ? part.productTitle() : "未匹配京东商品";
        String link = StringUtils.hasText(part.productLink()) ? part.productLink() : "无京东链接";
        String sku = StringUtils.hasText(part.skuId()) ? part.skuId() : "无";
        return hardwareTypeLabel(part.type().name()) + "：" + part.model()
                + "，京东商品：" + productTitle
                + "，价格：¥" + part.productPrice()
                + "，链接：" + link
                + "，SKU：" + sku
                + "，规格：" + part.specs();
    }

    private String hardwareTypeLabel(String type) {
        return switch (type) {
            case "CPU" -> "CPU";
            case "MOTHERBOARD" -> "主板";
            case "GPU" -> "显卡";
            case "MEMORY" -> "内存";
            case "STORAGE" -> "硬盘";
            case "PSU" -> "电源";
            case "CASE" -> "机箱";
            case "COOLER" -> "散热";
            default -> type;
        };
    }

    private String cleanReview(String value) {
        String cleaned = value == null ? "" : value
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
                .replaceAll("(?m)^\\s*[-•]\\s*", "")
                .trim();
        return normalizeCurrentHardwareReview(cleaned).trim();
    }

    private String normalizeCurrentHardwareReview(String value) {
        if (!containsAny(value, "RTX 50", "RTX50", "RTX 5050", "RTX5050", "RTX 5060", "RTX5060", "RTX 5070", "RTX5070", "RTX 5080", "RTX5080", "RTX 5090", "RTX5090")) {
            return value;
        }
        return value
                .replace("显卡型号存在不确定性", "显卡按配置单里的京东快照处理")
                .replace("京东链接可能无效", "购买前再确认当前京东页面库存和价格")
                .replace("尚未正式发布", "以当前京东页面库存和价格为准")
                .replace("未正式发布", "以当前京东页面库存和价格为准")
                .replace("尚未发布", "以当前京东页面库存和价格为准")
                .replace("未发布", "以当前京东页面库存和价格为准")
                .replace("尚未正式上市", "以当前京东页面库存和价格为准")
                .replace("未正式上市", "以当前京东页面库存和价格为准")
                .replace("未上市", "以当前京东页面库存和价格为准");
    }

    private boolean containsAny(String text, String... keywords) {
        String value = text == null ? "" : text;
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
