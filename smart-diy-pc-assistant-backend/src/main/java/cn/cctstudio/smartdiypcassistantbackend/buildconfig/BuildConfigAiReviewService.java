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
                要求：
                1. 不使用 Markdown、表格、星号、井号或代码块。
                2. 先给一句结论，再给 4 到 6 条短评价。
                3. 必须覆盖预算是否合理、核心性能、兼容性、散热和电源余量、后续升级空间。
                4. 如果配置中存在价格明显偏离预算、板U套装、缺少京东链接、无价格或不可判断库存，要直接提醒。
                5. 不要编造未给出的商品、价格或链接；所有商品价格以配置单里的京东快照为准。
                6. 不要用旧知识否定 RTX 50 系、RX 9000 系或配置单里已经出现京东链接/SKU 的商品；如需提醒，只能说以当前京东页面库存和价格为准。
                7. 控制在 260 到 420 个汉字之间，语气专业但直白。

                配置标题：%s
                总价：¥%d
                综合性能：%d 分
                兼容性：%s
                兼容性提示：%s
                配件：
                %s
                """.formatted(
                build.title(),
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
        return hardwareTypeLabel(part.type().name()) + "：" + part.model()
                + "，京东商品：" + productTitle
                + "，价格：¥" + part.productPrice()
                + "，链接：" + link
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
        return value == null ? "" : value
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
                .replaceAll("(?m)^\\s*[-•]\\s*", "")
                .trim();
    }
}
