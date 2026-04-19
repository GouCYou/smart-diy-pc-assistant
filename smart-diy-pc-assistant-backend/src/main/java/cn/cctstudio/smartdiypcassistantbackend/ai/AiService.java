package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatMessageResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatRequest;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ConversationResponse;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigService;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigSource;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigRequest;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.config.properties.AiProperties;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final AiProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final IntentRuleService intentRuleService;
    private final IntentAnalysisService intentAnalysisService;
    private final SafetyRuleService safetyRuleService;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final HardwarePartRepository hardwarePartRepository;
    private final BuildConfigService buildConfigService;

    public AiService(
            AiProperties properties,
            StringRedisTemplate redisTemplate,
            IntentRuleService intentRuleService,
            IntentAnalysisService intentAnalysisService,
            SafetyRuleService safetyRuleService,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            HardwarePartRepository hardwarePartRepository,
            BuildConfigService buildConfigService
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.intentRuleService = intentRuleService;
        this.intentAnalysisService = intentAnalysisService;
        this.safetyRuleService = safetyRuleService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.hardwarePartRepository = hardwarePartRepository;
        this.buildConfigService = buildConfigService;
    }

    @Transactional
    public ChatResponse chat(Long userId, ChatRequest request) {
        ChatConversation conversation = resolveConversation(userId, request.conversationId(), request.message());
        messageRepository.save(new ChatMessage(conversation.getId(), "user", request.message()));

        var direct = intentRuleService.match(request.message());
        if (direct.isPresent()) {
            String reply = direct.get().reply();
            return saveAssistantAndReturn(
                    conversation,
                    reply,
                    false,
                    true,
                    "规则直答",
                    direct.get().intent(),
                    new SafetyResult(SafetyLevel.PASS, ""),
                    null
            );
        }

        SafetyResult safety = safetyRuleService.check(request.message());
        if (safety.blocked()) {
            return saveAssistantAndReturn(
                    conversation,
                    safety.message(),
                    false,
                    true,
                    "安全规则",
                    "safety_block",
                    safety,
                    null
            );
        }

        IntentAnalysis intent = intentAnalysisService.analyze(request.message());
        BuildConfigResponse attachedBuild = request.buildConfigId() == null ? null : buildConfigService.get(userId, request.buildConfigId());
        BuildConfigResponse generatedBuild = maybeGenerateBuildConfig(userId, request.message(), attachedBuild);
        String conversationContext = buildConversationContext(conversation.getId());
        String buildContext = buildAiConfigContext(attachedBuild, generatedBuild);
        if (generatedBuild != null) {
            return saveAssistantAndReturn(
                    conversation,
                    createGeneratedBuildReply(attachedBuild != null, generatedBuild),
                    false,
                    false,
                    intent.intent(),
                    intent.subIntent(),
                    safety,
                    generatedBuild
            );
        }
        String cacheKey = "ai:chat:v5:"
                + conversation.getId()
                + ":"
                + Integer.toHexString(conversationContext.hashCode())
                + ":"
                + Integer.toHexString(buildContext.hashCode())
                + ":"
                + Integer.toHexString(request.message().hashCode())
                + ":" + intent.subIntent()
                + ":" + safety.level().name();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && generatedBuild == null) {
            return saveAssistantAndReturn(
                    conversation,
                    cached,
                    true,
                    false,
                    intent.intent(),
                    intent.subIntent(),
                    safety,
                    null
            );
        }

        String content = callModel(request.message(), conversationContext, buildContext, intent, safety);
        if (generatedBuild == null) {
            redisTemplate.opsForValue().set(cacheKey, content, CACHE_TTL);
        }
        return saveAssistantAndReturn(
                conversation,
                content,
                false,
                false,
                intent.intent(),
                intent.subIntent(),
                safety,
                generatedBuild
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(Long userId) {
        return conversationRepository.findTop30ByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(item -> new ConversationResponse(item.getId(), item.getTitle(), item.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(Long userId, Long conversationId) {
        ChatConversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("对话不存在"));
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(item -> new ChatMessageResponse(
                        item.getRole(),
                        item.getContent(),
                        item.getBuildConfigId(),
                        item.getBuildConfigTitle(),
                        item.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        ChatConversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("对话不存在"));
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    private String buildConversationContext(Long conversationId) {
        List<ChatMessage> recentMessages = new ArrayList<>(
                messageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(conversationId)
        );
        Collections.reverse(recentMessages);
        if (recentMessages.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage item : recentMessages) {
            String role = "assistant".equals(item.getRole()) ? "助手" : "用户";
            String content = item.getContent();
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            builder.append(role).append("：").append(content).append("\n");
        }
        return builder.toString().trim();
    }

    private BuildConfigResponse maybeGenerateBuildConfig(Long userId, String message, BuildConfigResponse attachedBuild) {
        if (!shouldGenerateBuild(message, attachedBuild != null)) {
            return null;
        }
        BuildPlan plan = selectBuildPlan(extractBudget(message));
        String title = createBuildTitle(message, attachedBuild != null, plan.totalPrice());
        String note = attachedBuild == null
                ? createBuildNote(message)
                : "基于《" + attachedBuild.title() + "》按你的新要求自动调整。需求：" + trim(message, 120);
        List<Long> partIds = plan.parts().stream().map(HardwarePart::getId).toList();
        return buildConfigService.create(userId, new BuildConfigRequest(title, note, partIds, BuildConfigSource.AI));
    }

    private String createGeneratedBuildReply(boolean adjusted, BuildConfigResponse build) {
        StringBuilder builder = new StringBuilder();
        builder.append(adjusted ? "已按你的要求重新调整并保存配置单：" : "已根据你的需求生成并保存配置单：")
                .append(build.title())
                .append("。总价 ¥")
                .append(build.totalPrice())
                .append("，综合性能 ")
                .append(build.performanceScore())
                .append(" 分。");
        builder.append("你可以点开下面的配置卡查看详情，也可以继续告诉我想降低预算、提高显卡性能、换白色外观或增强静音。");
        return builder.toString();
    }

    private boolean shouldGenerateBuild(String message, boolean hasAttachedBuild) {
        String text = message == null ? "" : message.toLowerCase();
        if (hasAttachedBuild) {
            return containsAny(text, "调整", "优化", "升级", "更换", "换成", "改成", "降预算", "提高性能");
        }
        boolean explicitBuild = containsAny(text, "生成配置", "配置单", "配一套", "装机方案", "推荐一套", "帮我配", "电脑配置", "装机", "配电脑");
        boolean budgetWithUse = containsAny(text, "预算", "主玩", "游戏", "2k", "4k", "剪辑", "建模", "办公", "生产力")
                && Pattern.compile("\\d{4,5}").matcher(text).find();
        return explicitBuild || budgetWithUse;
    }

    private String createBuildTitle(String message, boolean adjusted, int totalPrice) {
        String scene = "装机配置";
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("2k")) {
            scene = "2K 游戏配置";
        } else if (text.contains("4k")) {
            scene = "4K 游戏配置";
        } else if (text.contains("剪辑")) {
            scene = "剪辑创作配置";
        } else if (text.contains("建模")) {
            scene = "建模生产力配置";
        } else if (text.contains("办公")) {
            scene = "办公配置";
        } else if (text.contains("游戏") || text.contains("主玩")) {
            scene = "游戏配置";
        }
        return (adjusted ? "AI 调整 " : "AI 生成 ") + scene + " ¥" + totalPrice;
    }

    private String createBuildNote(String message) {
        return "根据你的需求自动生成。需求：" + trim(message, 160);
    }

    private String trim(String value, int maxLength) {
        String text = value == null ? "" : value.trim().replace("\n", " ");
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int extractBudget(String message) {
        Matcher matcher = Pattern.compile("(\\d{4,5})\\s*(元|块|预算)?").matcher(message == null ? "" : message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 6500;
    }

    private BuildPlan selectBuildPlan(int budget) {
        Map<HardwarePartType, List<HardwarePart>> parts = new EnumMap<>(HardwarePartType.class);
        for (HardwarePart part : hardwarePartRepository.findByEnabledTrue()) {
            parts.computeIfAbsent(part.getType(), ignored -> new ArrayList<>()).add(part);
        }
        for (List<HardwarePart> typedParts : parts.values()) {
            typedParts.sort(Comparator.comparing(HardwarePart::getPrice));
        }
        List<HardwarePart> cpus = required(parts, HardwarePartType.CPU);
        List<HardwarePart> motherboards = required(parts, HardwarePartType.MOTHERBOARD);
        List<HardwarePart> gpus = required(parts, HardwarePartType.GPU);
        List<HardwarePart> memories = required(parts, HardwarePartType.MEMORY);
        List<HardwarePart> storages = required(parts, HardwarePartType.STORAGE);
        List<HardwarePart> psus = required(parts, HardwarePartType.PSU);
        List<HardwarePart> cases = required(parts, HardwarePartType.CASE);
        List<HardwarePart> coolers = required(parts, HardwarePartType.COOLER);

        BuildPlan best = null;
        BuildPlan cheapest = null;
        for (HardwarePart cpu : cpus) {
            for (HardwarePart motherboard : motherboards) {
                if (!same(cpu.getSocketType(), motherboard.getSocketType())) {
                    continue;
                }
                for (HardwarePart memory : memories) {
                    if (!same(memory.getMemoryType(), motherboard.getMemoryType())) {
                        continue;
                    }
                    for (HardwarePart gpu : gpus) {
                        for (HardwarePart storage : storages) {
                            for (HardwarePart psu : psus) {
                                int requiredWattage = cpu.getWattage() + gpu.getWattage() + 120;
                                if (psu.getWattage() < requiredWattage) {
                                    continue;
                                }
                                for (HardwarePart pcCase : cases) {
                                    if (!pcCase.getSupportedFormFactors().toUpperCase().contains(motherboard.getFormFactor().toUpperCase())) {
                                        continue;
                                    }
                                    if (gpu.getGpuLengthMm() > 0 && pcCase.getGpuLengthMm() > 0 && gpu.getGpuLengthMm() > pcCase.getGpuLengthMm()) {
                                        continue;
                                    }
                                    for (HardwarePart cooler : coolers) {
                                        if (cooler.getCoolerHeightMm() > 0 && pcCase.getCoolerHeightMm() > 0 && cooler.getCoolerHeightMm() > pcCase.getCoolerHeightMm()) {
                                            continue;
                                        }
                                        List<HardwarePart> selected = List.of(cpu, motherboard, gpu, memory, storage, psu, pcCase, cooler);
                                        BuildPlan plan = new BuildPlan(selected, totalPrice(selected), performanceScore(selected));
                                        if (cheapest == null || plan.totalPrice() < cheapest.totalPrice()) {
                                            cheapest = plan;
                                        }
                                        if (plan.totalPrice() <= budget && isBetterPlan(plan, best, budget)) {
                                            best = plan;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        if (cheapest != null) {
            return cheapest;
        }
        throw new BusinessException("硬件库数据不足，暂时无法自动生成配置单");
    }

    private List<HardwarePart> required(Map<HardwarePartType, List<HardwarePart>> parts, HardwarePartType type) {
        List<HardwarePart> result = parts.getOrDefault(type, List.of());
        if (result.isEmpty()) {
            throw new BusinessException("硬件库缺少 " + type.name() + " 数据");
        }
        return result;
    }

    private boolean same(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }

    private boolean isBetterPlan(BuildPlan plan, BuildPlan current, int budget) {
        if (current == null) {
            return true;
        }
        if (plan.performanceScore() != current.performanceScore()) {
            return plan.performanceScore() > current.performanceScore();
        }
        int planBudgetDiff = Math.abs(budget - plan.totalPrice());
        int currentBudgetDiff = Math.abs(budget - current.totalPrice());
        if (planBudgetDiff != currentBudgetDiff) {
            return planBudgetDiff < currentBudgetDiff;
        }
        return plan.totalPrice() < current.totalPrice();
    }

    private int totalPrice(List<HardwarePart> parts) {
        return parts.stream().mapToInt(HardwarePart::getPrice).sum();
    }

    private int performanceScore(List<HardwarePart> parts) {
        int weighted = 0;
        int weight = 0;
        for (HardwarePart part : parts) {
            int partWeight = switch (part.getType()) {
                case CPU -> 30;
                case GPU -> 40;
                case MEMORY -> 12;
                case STORAGE -> 10;
                case MOTHERBOARD -> 8;
                default -> 0;
            };
            weighted += part.getPerformanceScore() * partWeight;
            weight += partWeight;
        }
        return weight == 0 ? 0 : Math.round((float) weighted / weight);
    }

    private String buildAiConfigContext(BuildConfigResponse attachedBuild, BuildConfigResponse generatedBuild) {
        StringBuilder builder = new StringBuilder();
        if (attachedBuild != null) {
            builder.append(formatBuildConfig("用户附加的配置单", attachedBuild)).append("\n\n");
        }
        if (generatedBuild != null) {
            builder.append(formatBuildConfig("已从数据库自动生成并保存的配置单", generatedBuild));
        }
        if (builder.isEmpty()) {
            return "无";
        }
        return builder.toString().trim();
    }

    private String formatBuildConfig(String label, BuildConfigResponse build) {
        StringBuilder builder = new StringBuilder();
        builder.append(label).append("：").append(build.title())
                .append("，总价 ¥").append(build.totalPrice())
                .append("，性能 ").append(build.performanceScore()).append(" 分。\n");
        for (HardwarePartResponse part : build.parts()) {
            builder.append(part.type()).append("：")
                    .append(part.brand()).append(" ")
                    .append(part.model()).append("，¥")
                    .append(part.price()).append("，性能 ")
                    .append(part.performanceScore()).append(" 分\n");
        }
        if (!build.note().isBlank()) {
            builder.append("备注：").append(build.note()).append("\n");
        }
        return builder.toString().trim();
    }

    private ChatConversation resolveConversation(Long userId, Long conversationId, String message) {
        if (conversationId != null) {
            return conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new BusinessException("对话不存在"));
        }
        return conversationRepository.save(new ChatConversation(userId, createTitle(message)));
    }

    private String createTitle(String message) {
        String text = message == null ? "新对话" : message.trim().replace("\n", " ");
        if (text.isBlank()) {
            return "新对话";
        }
        return text.length() > 28 ? text.substring(0, 28) + "..." : text;
    }

    private ChatResponse saveAssistantAndReturn(
            ChatConversation conversation,
            String content,
            boolean fromCache,
            boolean directReply,
            String intent,
            String subIntent,
            SafetyResult safety,
            BuildConfigResponse generatedBuild
    ) {
        messageRepository.save(new ChatMessage(
                conversation.getId(),
                "assistant",
                content,
                generatedBuild == null ? null : generatedBuild.id(),
                generatedBuild == null ? "" : generatedBuild.title()
        ));
        conversation.touch();
        return new ChatResponse(
                conversation.getId(),
                content,
                fromCache,
                directReply,
                intent,
                subIntent,
                safety.level().name(),
                safety.message(),
                generatedBuild == null ? null : generatedBuild.id(),
                generatedBuild == null ? "" : generatedBuild.title(),
                generatedBuild
        );
    }

    private String callModel(String message, String conversationContext, String buildContext, IntentAnalysis intent, SafetyResult safety) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return "AI 服务尚未配置密钥，请稍后再试。";
        }
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();
        String prompt = """
                你是智能 DIY 电脑助手。请用简体中文回答，重点关注预算、用途、兼容性、散热、电源和升级空间。
                回复要求：
                1. 不要使用 Markdown 语法，不要使用星号、井号、表格或代码块。
                2. 控制在 180 到 420 个汉字之间，信息要完整但不要啰嗦。
                3. 优先给出结论，再给 3 到 5 条关键理由或建议。
                4. 如果信息不足，最后用一句话追问最关键的缺失条件。
                5. 如果安全等级是 WARN 或 URGENT，必须明确提醒用户先确认关键风险，不能直接给出“放心买”的结论。
                6. 需要结合当前会话上下文回答，但不要提及“根据上下文”这类字样。
                7. 如果系统提供了已生成配置单，直接说明已保存到配置页，并围绕该配置做简洁解释。
                8. 如果用户附加了配置单，优先围绕附加配置单评价、优化或回答问题。

                意图识别：
                大类：%s
                子意图：%s
                识别依据：%s
                提取参数：%s
                第二层建议进入安全检查：%s
                第二层风险等级：%s

                安全规则：
                等级：%s
                提醒：%s

                当前会话上下文：
                %s

                配置单上下文：
                %s

                用户需求：
                %s
                """.formatted(
                intent.intent(),
                intent.subIntent(),
                intent.reasoning(),
                intent.extractedDataJson(),
                intent.safetyCheckRequired() ? "是" : "否",
                intent.safetyRiskLevel(),
                safety.level().name(),
                safety.message().isBlank() ? "无" : safety.message(),
                conversationContext,
                buildContext,
                message
        );
        return model.generate(prompt);
    }

    private record BuildPlan(List<HardwarePart> parts, int totalPrice, int performanceScore) {
    }
}
