package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatMessageResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.AiBuildTaskResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatNewsReferenceResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatRequest;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ConversationResponse;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigService;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.BuildConfigSource;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildPartSelectionRequest;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigRequest;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.BuildConfigResponse;
import cn.cctstudio.smartdiypcassistantbackend.commerce.JdProduct;
import cn.cctstudio.smartdiypcassistantbackend.commerce.JdProductSearchService;
import cn.cctstudio.smartdiypcassistantbackend.config.properties.AiProperties;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartRepository;
import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePartType;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.HardwarePartResponse;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.news.News;
import cn.cctstudio.smartdiypcassistantbackend.news.NewsRepository;
import cn.cctstudio.smartdiypcassistantbackend.news.NewsStatus;
import cn.cctstudio.smartdiypcassistantbackend.user.UserAccessService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration CHAT_DUPLICATE_TTL = Duration.ofMinutes(2);
    private static final Pattern BOARD_CHIPSET_PATTERN = Pattern.compile("[ABHXZ]\\d{3,4}[A-Z]{0,3}", Pattern.CASE_INSENSITIVE);

    private final AiProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final IntentRuleService intentRuleService;
    private final IntentAnalysisService intentAnalysisService;
    private final SafetyRuleService safetyRuleService;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AiBuildTaskRepository buildTaskRepository;
    private final MemoryService memoryService;
    private final MemoryExtractor memoryExtractor;
    private final HardwarePartRepository hardwarePartRepository;
    private final BuildConfigService buildConfigService;
    private final UserAccessService userAccessService;
    private final JdProductSearchService jdProductSearchService;
    private final AiSettingsService aiSettingsService;
    private final NewsRepository newsRepository;
    private final MediaUrlService mediaUrlService;
    private final Executor aiBuildTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService(
            AiProperties properties,
            StringRedisTemplate redisTemplate,
            IntentRuleService intentRuleService,
            IntentAnalysisService intentAnalysisService,
            SafetyRuleService safetyRuleService,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            AiBuildTaskRepository buildTaskRepository,
            MemoryService memoryService,
            MemoryExtractor memoryExtractor,
            HardwarePartRepository hardwarePartRepository,
            BuildConfigService buildConfigService,
            UserAccessService userAccessService,
            JdProductSearchService jdProductSearchService,
            AiSettingsService aiSettingsService,
            NewsRepository newsRepository,
            MediaUrlService mediaUrlService,
            @Qualifier("aiBuildTaskExecutor") Executor aiBuildTaskExecutor,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.intentRuleService = intentRuleService;
        this.intentAnalysisService = intentAnalysisService;
        this.safetyRuleService = safetyRuleService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.buildTaskRepository = buildTaskRepository;
        this.memoryService = memoryService;
        this.memoryExtractor = memoryExtractor;
        this.hardwarePartRepository = hardwarePartRepository;
        this.buildConfigService = buildConfigService;
        this.userAccessService = userAccessService;
        this.jdProductSearchService = jdProductSearchService;
        this.aiSettingsService = aiSettingsService;
        this.newsRepository = newsRepository;
        this.mediaUrlService = mediaUrlService;
        this.aiBuildTaskExecutor = aiBuildTaskExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedBuildTasks() {
        List<Long> taskIds = transactionTemplate.execute(status -> {
            List<AiBuildTask> tasks = buildTaskRepository.findByStatusInOrderByUpdatedAtAsc(
                    List.of(AiBuildTaskStatus.QUEUED, AiBuildTaskStatus.RUNNING)
            );
            for (AiBuildTask task : tasks) {
                task.markRunning(
                        Math.max(8, Math.min(task.getProgress(), 88)),
                        "恢复生成",
                        "服务已启动，继续京东商品检索"
                );
            }
            return tasks.stream().map(AiBuildTask::getId).toList();
        });
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        for (Long taskId : taskIds) {
            scheduleBuildTaskAfterCommit(taskId);
        }
    }

    @Transactional
    public ChatResponse chat(Long userId, ChatRequest request) {
        userAccessService.requireAvailable(userId);
        String duplicateKey = chatDuplicateKey(userId, request);
        if (!acquireChatDuplicateGuard(duplicateKey)) {
            throw new BusinessException("这条消息正在处理中，请稍候");
        }
        try {
            return doChat(userId, request);
        } catch (RuntimeException ex) {
            releaseChatDuplicateGuard(duplicateKey);
            throw ex;
        }
    }

    private ChatResponse doChat(Long userId, ChatRequest request) {
        AiSettingsService.EffectiveAiSettings settings = aiSettingsService.effective();
        ChatConversation conversation = resolveConversation(userId, request.conversationId(), request.message());
        messageRepository.save(new ChatMessage(conversation.getId(), "user", request.message()));

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

        UserMemory userMemory = mergeUserMemory(userId, request.message());
        BuildConfigResponse attachedBuild = request.buildConfigId() == null ? null : buildConfigService.get(userId, request.buildConfigId());
        IntentAnalysis intent = intentAnalysisService.analyze(request.message(), attachedBuild != null, settings);
        var direct = intentRuleService.match(request.message());
        if (direct.isPresent() && shouldUseDirectRule(intent)) {
            String reply = direct.get().reply();
            return saveAssistantAndReturn(
                    conversation,
                    reply,
                    false,
                    true,
                    "规则直答",
                    direct.get().intent(),
                    safety,
                    null
            );
        }

        if (settings.autoBuildEnabled() && intent.toolAction().startsBuildTask()) {
            if (shouldGenerateBuild(request.message(), attachedBuild != null, settings)) {
                return startAsyncBuildTask(userId, conversation, request, attachedBuild, intent, safety);
            }
            intent = intent.withToolAction(
                    AiToolAction.ASK_CLARIFICATION,
                    0.82,
                    "模型倾向调用配置单工具，但后端确认缺少预算、用途或明确生成动作，改为先追问"
            );
        }

        String memoryContext = memoryService.formatPromptContext(userMemory);
        BuildConfigResponse generatedBuild = null;
        String conversationContext = buildConversationContext(conversation.getId(), settings.contextMessageLimit());
        String buildContext = buildAiConfigContext(attachedBuild, generatedBuild);
        NewsContext newsContext = intent.toolAction() == AiToolAction.SUMMARIZE_NEWS
                ? buildNewsContext(request.message(), true)
                : NewsContext.empty();
        String cacheKey = "ai:chat:v5:"
                + conversation.getId()
                + ":"
                + Integer.toHexString(memoryContext.hashCode())
                + ":"
                + Integer.toHexString(conversationContext.hashCode())
                + ":"
                + Integer.toHexString(buildContext.hashCode())
                + ":"
                + Integer.toHexString(newsContext.promptContext().hashCode())
                + ":"
                + Integer.toHexString(request.message().hashCode())
                + ":" + intent.subIntent()
                + ":" + intent.toolAction()
                + ":" + safety.level().name()
                + ":" + Integer.toHexString(settings.cacheSignature().hashCode());
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
                    null,
                    newsContext.references()
            );
        }

        String content = callModel(request.message(), memoryContext, conversationContext, buildContext, newsContext.promptContext(), intent, safety, settings);
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
                generatedBuild,
                newsContext.references()
        );
    }

    private boolean shouldUseDirectRule(IntentAnalysis intent) {
        return intent == null
                || (intent.toolAction() != AiToolAction.SUMMARIZE_NEWS && !intent.toolAction().startsBuildTask());
    }

    private ChatResponse startAsyncBuildTask(
            Long userId,
            ChatConversation conversation,
            ChatRequest request,
            BuildConfigResponse attachedBuild,
            IntentAnalysis intent,
            SafetyResult safety
    ) {
        AiBuildTask task = buildTaskRepository.save(new AiBuildTask(
                userId,
                conversation.getId(),
                attachedBuild == null ? null : attachedBuild.id(),
                request.message()
        ));
        conversation.touch();
        scheduleBuildTaskAfterCommit(task.getId());
        return new ChatResponse(
                conversation.getId(),
                "已开始生成京东可购买配置单，我会逐步检索京东商品并保存结果。",
                false,
                false,
                intent.intent(),
                intent.subIntent(),
                safety.level().name(),
                safety.message(),
                null,
                "",
                "",
                null,
                List.of(),
                AiBuildTaskResponse.from(task)
        );
    }

    private void scheduleBuildTaskAfterCommit(Long taskId) {
        Runnable runner = () -> aiBuildTaskExecutor.execute(() -> processBuildTask(taskId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runner.run();
                }
            });
            return;
        }
        runner.run();
    }

    private void processBuildTask(Long taskId) {
        BuildTaskSnapshot snapshot = loadBuildTaskSnapshot(taskId);
        if (snapshot == null) {
            return;
        }
        try {
            updateBuildTaskProgress(taskId, 12, "识别需求", "整理预算、用途和偏好");
            AiSettingsService.EffectiveAiSettings settings = aiSettingsService.effective();
            UserMemory userMemory = memoryService.findByUserId(snapshot.userId()).orElse(null);
            BuildConfigResponse attachedBuild = snapshot.attachedBuildId() == null
                    ? null
                    : buildConfigService.get(snapshot.userId(), snapshot.attachedBuildId());
            updateBuildTaskProgress(taskId, 24, "筛选硬件", "生成少量可行候选方案");
            BuildGenerationResult generation = maybeGenerateBuildConfig(
                    snapshot.userId(),
                    snapshot.message(),
                    attachedBuild,
                    settings,
                    userMemory,
                    (progress, title, description) -> updateBuildTaskProgress(taskId, progress, title, description)
            );
            if (generation.failed()) {
                throw new BusinessException(generation.failureMessage());
            }
            BuildConfigResponse build = generation.build();
            if (build == null) {
                throw new BusinessException("没有生成可保存的京东配置单");
            }
            updateBuildTaskProgress(taskId, 96, "整理回复", "保存配置单和对话结果");
            String reply = createGeneratedBuildReply(attachedBuild != null, build);
            transactionTemplate.executeWithoutResult(status -> {
                ChatConversation conversation = conversationRepository.findById(snapshot.conversationId()).orElse(null);
                if (conversation != null) {
                    messageRepository.save(new ChatMessage(
                            snapshot.conversationId(),
                            "assistant",
                            reply,
                            build.id(),
                            build.title(),
                            ""
                    ));
                    conversation.touch();
                }
                buildTaskRepository.findById(taskId).ifPresent(task -> task.markSucceeded(
                        build.id(),
                        build.title(),
                        build.serialNumber()
                ));
            });
        } catch (Exception exception) {
            String message = exception instanceof BusinessException businessException
                    ? businessException.getMessage()
                    : "京东商品检索或配置保存失败，请稍后重试。";
            transactionTemplate.executeWithoutResult(status -> {
                ChatConversation conversation = conversationRepository.findById(snapshot.conversationId()).orElse(null);
                if (conversation != null) {
                    messageRepository.save(new ChatMessage(
                            snapshot.conversationId(),
                            "assistant",
                            createBuildGenerationFailureReply(message)
                    ));
                    conversation.touch();
                }
                buildTaskRepository.findById(taskId).ifPresent(task -> task.markFailed(message));
            });
        }
    }

    private BuildTaskSnapshot loadBuildTaskSnapshot(Long taskId) {
        return transactionTemplate.execute(status -> buildTaskRepository.findById(taskId)
                .map(task -> new BuildTaskSnapshot(
                        task.getId(),
                        task.getUserId(),
                        task.getConversationId(),
                        task.getAttachedBuildId(),
                        task.getRequestMessage()
                ))
                .orElse(null));
    }

    private void updateBuildTaskProgress(Long taskId, int progress, String title, String description) {
        transactionTemplate.executeWithoutResult(status -> buildTaskRepository.findById(taskId)
                .ifPresent(task -> task.markRunning(progress, title, description)));
    }

    @Transactional(readOnly = true)
    public AiBuildTaskResponse getBuildTask(Long userId, Long taskId) {
        return buildTaskRepository.findByIdAndUserId(taskId, userId)
                .map(AiBuildTaskResponse::from)
                .orElseThrow(() -> new BusinessException("生成任务不存在"));
    }

    @Transactional(readOnly = true)
    public List<AiBuildTaskResponse> listActiveBuildTasks(Long userId, Long conversationId) {
        List<AiBuildTaskStatus> activeStatuses = List.of(AiBuildTaskStatus.QUEUED, AiBuildTaskStatus.RUNNING);
        List<AiBuildTask> tasks;
        if (conversationId != null && conversationId > 0) {
            tasks = buildTaskRepository.findByUserIdAndConversationIdAndStatusInOrderByUpdatedAtDesc(
                    userId,
                    conversationId,
                    activeStatuses
            );
        } else {
            tasks = buildTaskRepository.findTop5ByUserIdAndStatusInOrderByUpdatedAtDesc(userId, activeStatuses);
        }
        return tasks.stream().map(AiBuildTaskResponse::from).toList();
    }

    private String chatDuplicateKey(Long userId, ChatRequest request) {
        String message = request.message() == null ? "" : request.message().trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String conversationPart = request.conversationId() == null ? "new" : request.conversationId().toString();
        String buildPart = request.buildConfigId() == null ? "none" : request.buildConfigId().toString();
        String signature = conversationPart + ":" + buildPart + ":" + message;
        return "ai:chat:dedupe:" + userId + ":" + Integer.toHexString(signature.hashCode());
    }

    private boolean acquireChatDuplicateGuard(String duplicateKey) {
        if (!StringUtils.hasText(duplicateKey)) {
            return true;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(duplicateKey, "1", CHAT_DUPLICATE_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseChatDuplicateGuard(String duplicateKey) {
        if (StringUtils.hasText(duplicateKey)) {
            redisTemplate.delete(duplicateKey);
        }
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
                        resolveBuildSerialNumber(item.getBuildConfigId()),
                        item.getCreatedAt(),
                        parseNewsReferences(item.getNewsReferencesJson())
                ))
                .toList();
    }

    private String resolveBuildSerialNumber(Long buildConfigId) {
        if (buildConfigId == null || buildConfigId <= 0) {
            return "";
        }
        BuildConfigResponse build = buildConfigService.getPublic(buildConfigId);
        return build == null ? "" : build.serialNumber();
    }

    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        ChatConversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("对话不存在"));
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    private String buildConversationContext(Long conversationId, int messageLimit) {
        List<ChatMessage> recentMessages = new ArrayList<>(
                messageRepository.findByConversationIdOrderByCreatedAtDesc(
                        conversationId,
                        PageRequest.of(0, Math.max(4, Math.min(30, messageLimit)))
                )
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

    private NewsContext buildNewsContext(String message) {
        return buildNewsContext(message, false);
    }

    private NewsContext buildNewsContext(String message, boolean forced) {
        if (!forced && !shouldUseNewsContext(message)) {
            return NewsContext.empty();
        }
        List<News> publishedNews = newsRepository.findByStatusOrderByCreatedAtDesc(NewsStatus.PUBLISHED);
        if (publishedNews.isEmpty()) {
            return NewsContext.empty();
        }
        List<NewsMatch> matches = publishedNews.stream()
                .map(news -> new NewsMatch(news, scoreNews(news, message)))
                .toList();
        boolean hasRelevantMatch = matches.stream().anyMatch(match -> match.score() > 0);
        List<NewsMatch> selected = matches.stream()
                .filter(match -> !hasRelevantMatch || match.score() > 0)
                .sorted((left, right) -> {
                    int byScore = Integer.compare(right.score(), left.score());
                    if (byScore != 0) {
                        return byScore;
                    }
                    return right.news().getCreatedAt().compareTo(left.news().getCreatedAt());
                })
                .limit(3)
                .toList();
        if (selected.isEmpty()) {
            return NewsContext.empty();
        }
        StringBuilder builder = new StringBuilder("可引用的热点新闻：\n");
        List<ChatNewsReferenceResponse> references = new ArrayList<>();
        for (NewsMatch match : selected) {
            News news = match.news();
            String summary = clampNewsText(firstText(news.getSummary(), news.getContent(), news.getTitle()), 180);
            references.add(new ChatNewsReferenceResponse(
                    news.getId(),
                    news.getTitle(),
                    summary,
                    news.getTag(),
                    mediaUrlService.toPublicUrl(news.getCoverKey())
            ));
            builder.append("新闻 ID ").append(news.getId())
                    .append("：").append(news.getTitle())
                    .append("；标签：").append(news.getTag())
                    .append("；摘要：").append(summary)
                    .append("；正文摘录：").append(clampNewsText(news.getContent(), 520))
                    .append("\n");
        }
        return new NewsContext(builder.toString().trim(), references);
    }

    private boolean shouldUseNewsContext(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (containsAny(text, "新闻", "热点", "资讯", "报道", "爆料", "发布会", "原文")) {
            return true;
        }
        return containsAny(text, "最近", "最新", "今天", "这几天", "近期")
                && containsAny(text, "显卡", "cpu", "处理器", "硬件", "电脑", "装机", "英伟达", "nvidia", "amd", "intel", "英特尔", "锐龙", "酷睿", "主板", "内存", "固态");
    }

    private int scoreNews(News news, String message) {
        String query = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String title = news.getTitle() == null ? "" : news.getTitle().toLowerCase(Locale.ROOT);
        String tag = news.getTag() == null ? "" : news.getTag().toLowerCase(Locale.ROOT);
        String content = news.getContent() == null ? "" : news.getContent().toLowerCase(Locale.ROOT);
        String haystack = title + "\n" + tag + "\n" + content;
        int score = 0;
        for (String keyword : newsKeywords(query)) {
            if (title.contains(keyword)) {
                score += 4;
            } else if (tag.contains(keyword)) {
                score += 3;
            } else if (haystack.contains(keyword)) {
                score += 2;
            }
        }
        return score;
    }

    private List<String> newsKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        String[] known = {
                "nvidia", "英伟达", "rtx", "geforce", "amd", "radeon", "rx", "intel", "英特尔",
                "cpu", "gpu", "显卡", "处理器", "主板", "内存", "固态", "硬盘", "电源", "机箱",
                "锐龙", "酷睿", "x3d", "ai", "cuda", "游戏", "剪辑", "生产力", "装机"
        };
        for (String keyword : known) {
            if (query.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        for (String token : query.split("[\\s,，。！？!?.、:：;；]+")) {
            if (token.length() >= 2 && token.length() <= 24) {
                keywords.add(token);
            }
        }
        return keywords.stream().distinct().toList();
    }

    private String firstText(String primary, String secondary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        if (StringUtils.hasText(secondary)) {
            return secondary;
        }
        return fallback == null ? "" : fallback;
    }

    private String clampNewsText(String value, int maxLength) {
        String text = value == null ? "" : value
                .replaceAll("!\\[[^]]*]\\([^)]+\\)", " ")
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("[#>*_`~-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private BuildGenerationResult maybeGenerateBuildConfig(
            Long userId,
            String message,
            BuildConfigResponse attachedBuild,
            AiSettingsService.EffectiveAiSettings settings,
            UserMemory userMemory
    ) {
        return maybeGenerateBuildConfig(userId, message, attachedBuild, settings, userMemory, null);
    }

    private BuildGenerationResult maybeGenerateBuildConfig(
            Long userId,
            String message,
            BuildConfigResponse attachedBuild,
            AiSettingsService.EffectiveAiSettings settings,
            UserMemory userMemory,
            BuildProgressReporter progressReporter
    ) {
        if (!settings.autoBuildEnabled() || !shouldGenerateBuild(message, attachedBuild != null, settings)) {
            return BuildGenerationResult.none();
        }
        BuildPlan plan;
        try {
            plan = selectBuildPlan(preferredBudget(message, userMemory), message, settings, userMemory, progressReporter);
        } catch (BusinessException exception) {
            return BuildGenerationResult.failed(exception.getMessage());
        }
        BuildPreferences preferences = buildPreferences(message, userMemory);
        String colorNote = createColorBuildNote(plan.selections(), preferences, settings);
        String title = createBuildTitle(message, attachedBuild != null);
        String note = attachedBuild == null
                ? createBuildNote(message)
                : "基于《" + attachedBuild.title() + "》按你的新要求自动调整。需求：" + trim(message, 120);
        if (StringUtils.hasText(colorNote)) {
            note = note + " " + colorNote;
        }
        List<Long> partIds = plan.parts().stream().map(HardwarePart::getId).toList();
        return BuildGenerationResult.generated(buildConfigService.create(userId, new BuildConfigRequest(title, note, partIds, plan.selections(), BuildConfigSource.AI)));
    }

    private String createBuildGenerationFailureReply(String failureMessage) {
        String reason = StringUtils.hasText(failureMessage) ? failureMessage : "自动匹配商品时没有拿到完整结果";
        return "我识别到你想生成配置单，但这次没有保存成功。原因是：" + reason
                + "\n\n为了避免把无价格、无链接或不可购买的商品写进配置单，我不会用本地参考价强行生成。你可以稍后再试，或者先到配置页手动选择京东商品。";
    }

    private UserMemory mergeUserMemory(Long userId, String message) {
        UserMemoryPatch patch = memoryExtractor.extract(message);
        if (patch.hasAnyValue()) {
            return memoryService.merge(userId, patch);
        }
        return memoryService.findByUserId(userId).orElse(null);
    }

    private String createGeneratedBuildReply(boolean adjusted, BuildConfigResponse build) {
        StringBuilder builder = new StringBuilder();
        builder.append(adjusted ? "已按你的要求重新调整并保存配置单：" : "已根据你的需求生成并保存配置单：")
                .append(build.title())
                .append("。总价 ¥")
                .append(build.totalPrice())
                .append("，综合性能 ")
                .append(build.performanceScore())
                .append(" 分。\n\n");
        builder.append(createBuildReason(build))
                .append("\n\n");
        String fpsAdvice = createFpsAdvice(build);
        if (StringUtils.hasText(fpsAdvice)) {
            builder.append(fpsAdvice).append("\n\n");
        }
        String colorNote = extractColorNote(build.note());
        if (StringUtils.hasText(colorNote)) {
            builder.append(colorNote).append("\n\n");
        }
        builder.append("你可以点开下面的配置卡查看详情，也可以继续告诉我想降低预算、提高显卡性能或增强静音。");
        return builder.toString();
    }

    private String extractColorNote(String note) {
        if (!StringUtils.hasText(note) || !note.contains("外观说明：")) {
            return "";
        }
        int start = note.indexOf("外观说明：");
        int end = note.indexOf("。", start);
        return end > start ? note.substring(start, end + 1) : note.substring(start);
    }

    private String createBuildReason(BuildConfigResponse build) {
        HardwarePartResponse cpu = findBuildPart(build, HardwarePartType.CPU);
        HardwarePartResponse gpu = findBuildPart(build, HardwarePartType.GPU);
        HardwarePartResponse motherboard = findBuildPart(build, HardwarePartType.MOTHERBOARD);
        HardwarePartResponse memory = findBuildPart(build, HardwarePartType.MEMORY);
        HardwarePartResponse psu = findBuildPart(build, HardwarePartType.PSU);
        StringBuilder reason = new StringBuilder();
        boolean productivity = containsAny(build.note().toLowerCase(), "生产力", "剪辑", "渲染", "建模", "编程", "设计");
        if (cpu != null && gpu != null) {
            reason.append("这套把预算优先放在 ").append(partDisplayName(gpu))
                    .append(" 和 ").append(partDisplayName(cpu))
                    .append(productivity ? " 上，优先照顾多线程处理、渲染加速和多任务响应。" : " 上，更贴近游戏负载和日常响应。");
        }
        if (motherboard != null) {
            reason.append("主板用 ").append(partDisplayName(motherboard))
                    .append("，重点匹配接口、内存类型和后续升级空间。");
        }
        if (memory != null) {
            reason.append("内存选择 ").append(memory.memoryType()).append(" 平台，和主板保持一致。");
        }
        if (psu != null && psu.wattage() != null && psu.wattage() > 0) {
            reason.append("电源预留到 ").append(psu.wattage()).append("W，给显卡满载和后期升级留余量。");
        }
        return reason.toString();
    }

    private HardwarePartResponse findBuildPart(BuildConfigResponse build, HardwarePartType type) {
        return build.parts().stream()
                .filter(part -> part.type() == type)
                .findFirst()
                .orElse(null);
    }

    private String partDisplayName(HardwarePartResponse part) {
        if (part.type() == HardwarePartType.MOTHERBOARD
                && StringUtils.hasText(part.productTitle())
                && part.productTitle().contains("已包含在 CPU 板U套装中")) {
            return part.model();
        }
        if (StringUtils.hasText(part.productTitle())) {
            return trim(part.productTitle(), 30) + "（" + part.model() + "）";
        }
        return part.model();
    }

    private String createFpsAdvice(BuildConfigResponse build) {
        String note = build.note() == null ? "" : build.note().toLowerCase(Locale.ROOT);
        String game = detectGameName(note);
        if (!StringUtils.hasText(game)) {
            return "";
        }
        HardwarePartResponse cpu = findBuildPart(build, HardwarePartType.CPU);
        HardwarePartResponse gpu = findBuildPart(build, HardwarePartType.GPU);
        if (cpu == null || gpu == null) {
            return "";
        }
        int estimate = estimateGameFps(game, cpu.performanceScore(), gpu.performanceScore());
        int low = Math.max(30, Math.round(estimate * 0.82f / 10f) * 10);
        int high = Math.max(low + 10, Math.round(estimate * 1.08f / 10f) * 10);
        Integer target = extractRequestedFps(build.note());
        if (target != null && target > high) {
            return "帧率预估：按 " + partDisplayName(gpu) + " 和 " + partDisplayName(cpu)
                    + " 保守估算，" + game + " 大约在 " + low + "-" + high + " 帧。你提到的 "
                    + target + " 帧这个预算很难稳定达到，需要加预算、降低画质或降低分辨率目标。";
        }
        return "帧率预估：按这套 CPU 和显卡的定位，" + game + " 保守估算大约 " + low + "-" + high + " 帧，实际会受分辨率、画质和地图场景影响。";
    }

    private String detectGameName(String text) {
        if (containsAny(text, "cs2", "反恐精英2", "csgo")) {
            return "CS2";
        }
        if (containsAny(text, "无畏契约", "valorant")) {
            return "无畏契约";
        }
        if (containsAny(text, "永劫无间")) {
            return "永劫无间";
        }
        if (containsAny(text, "三角洲", "delta force")) {
            return "三角洲行动";
        }
        if (containsAny(text, "黑神话", "悟空")) {
            return "黑神话：悟空";
        }
        return "";
    }

    private int estimateGameFps(String game, Integer cpuScore, Integer gpuScore) {
        int cpu = cpuScore == null ? 0 : cpuScore;
        int gpu = gpuScore == null ? 0 : gpuScore;
        if ("CS2".equals(game) || "无畏契约".equals(game)) {
            return Math.round(cpu * 3.2f + gpu * 1.4f);
        }
        if ("三角洲行动".equals(game) || "永劫无间".equals(game)) {
            return Math.round(cpu * 0.7f + gpu * 2.0f);
        }
        if ("黑神话：悟空".equals(game)) {
            return Math.round(cpu * 0.35f + gpu * 1.35f);
        }
        return Math.round(cpu * 0.7f + gpu * 1.8f);
    }

    private Integer extractRequestedFps(String note) {
        Matcher matcher = Pattern.compile("(\\d{2,4})\\s*(?:帧|fps)", Pattern.CASE_INSENSITIVE).matcher(note == null ? "" : note);
        if (!matcher.find()) {
            return null;
        }
        return parseInteger(matcher.group(1));
    }

    private boolean shouldGenerateBuild(String message, boolean hasAttachedBuild, AiSettingsService.EffectiveAiSettings settings) {
        String text = message == null ? "" : message.toLowerCase();
        if (hasAttachedBuild) {
            return containsAny(text, "调整", "优化", "升级", "更换", "换成", "改成", "降预算", "提高性能", "补齐", "生成", "保存", "重新配", "再来一套");
        }
        boolean explicitBuild = isExplicitBuildRequest(text);
        if (isRiskOrCompatibilityQuestion(text) && !explicitBuild) {
            return false;
        }
        boolean hasBudget = hasBudgetAmount(text);
        boolean useScene = containsAny(text, "预算", "主玩", "游戏", "2k", "4k", "剪辑", "建模", "办公", "生产力", "日常", "影音", "渲染", "ai", "深度学习");
        boolean componentIntent = containsAny(text, "cpu", "处理器", "显卡", "内存", "主板", "机箱", "电源", "固态")
                && containsAny(text, "预算", "配置", "帮我", "组", "配", "补齐");
        boolean buildSubject = containsAny(text, "配置", "装机", "电脑", "台式机", "主机", "台式", "游戏", "办公");
        return switch (settings.buildTriggerMode()) {
            case CONSERVATIVE -> explicitBuild || (hasBudget && containsAny(text, "配置", "装机", "电脑", "台式机", "主机"));
            case BALANCED -> explicitBuild || (hasBudget && (useScene || buildSubject)) || componentIntent;
            case SENSITIVE -> explicitBuild || (hasBudget && (useScene || buildSubject)) || componentIntent;
        };
    }

    private boolean isExplicitBuildRequest(String text) {
        return containsAny(text,
                "生成配置", "生成一套", "保存配置", "具体配置", "配一套", "组一套", "攒一套",
                "出一套", "出一台", "来一套", "来一台", "配台", "组台", "装一台",
                "推荐一套", "帮我配", "帮我组", "帮我攒",
                "配电脑", "组电脑", "攒机");
    }

    private boolean isRiskOrCompatibilityQuestion(String text) {
        boolean question = containsAny(text,
                "吗", "？", "?", "能不能", "可不可以", "可以不", "可以用", "能用",
                "行不行", "能装", "能买", "能买吗", "能直接买", "要不要", "是否",
                "靠谱吗", "安全吗", "会不会", "有没有", "为什么", "怎么", "如何");
        if (!question) {
            return false;
        }
        return containsAny(text,
                "山寨", "杂牌", "三无", "劣质", "假电源", "来源不明", "二手", "老电源",
                "电源", "bios", "散热", "限高", "显卡长度", "兼容", "冲突", "点亮",
                "风险", "危险", "炸", "烧", "短路");
    }

    private String createBuildTitle(String message, boolean adjusted) {
        String scene = "装机配置";
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("2k")) {
            scene = "2K 游戏配置";
        } else if (text.contains("4k")) {
            scene = "4K 游戏配置";
        } else if (containsAny(text, "生产力", "渲染", "编程", "设计")) {
            scene = "生产力配置";
        } else if (text.contains("剪辑")) {
            scene = "剪辑创作配置";
        } else if (text.contains("建模")) {
            scene = "建模生产力配置";
        } else if (text.contains("办公")) {
            scene = "办公配置";
        } else if (text.contains("游戏") || text.contains("主玩")) {
            scene = "游戏配置";
        }
        return (adjusted ? "AI 调整 " : "AI 生成 ") + scene;
    }

    private String createBuildNote(String message) {
        return "根据你的需求自动生成。需求：" + trim(message, 160);
    }

    private String createColorBuildNote(
            List<BuildPartSelectionRequest> selections,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        if (settings.colorMatchMode() == AiColorMatchMode.FLEXIBLE) {
            return "";
        }
        if (!preferences.explicitColorPreference() || !StringUtils.hasText(preferences.preferredColor())) {
            return "";
        }
        String targetColor = preferences.preferredColor();
        if (!StringUtils.hasText(targetColor)) {
            return "";
        }
        int known = 0;
        int matched = 0;
        for (BuildPartSelectionRequest selection : selections) {
            if (selection == null || !StringUtils.hasText(selection.title()) || !isVisualPartTitle(selection.title())) {
                continue;
            }
            String color = productColor(selection.title());
            if (!StringUtils.hasText(color)) {
                continue;
            }
            known++;
            if (targetColor.equals(color)) {
                matched++;
            }
        }
        String colorLabel = "WHITE".equals(targetColor) ? "白色" : "黑色";
        if (known == 0) {
            return "";
        }
        if (matched == known) {
            return "外观说明：已优先匹配" + colorLabel + "系配件。";
        }
        return "外观说明：已优先匹配" + colorLabel + "系配件，但部分京东可购买商品颜色不完整，已在兼容、预算和可购买之间做折中。";
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
        Integer budget = extractExplicitBudget(message);
        return budget == null ? 6500 : budget;
    }

    private int preferredBudget(String message, UserMemory userMemory) {
        Integer currentBudget = extractExplicitBudget(message);
        if (currentBudget != null) {
            return currentBudget;
        }
        if (userMemory != null && StringUtils.hasText(userMemory.getBudgetPreference())) {
            Integer memoryBudget = extractExplicitBudget(userMemory.getBudgetPreference());
            if (memoryBudget != null) {
                return memoryBudget;
            }
        }
        return 6500;
    }

    private Integer extractExplicitBudget(String message) {
        String text = message == null ? "" : message;
        Matcher matcher = Pattern.compile("(\\d{4,5})\\s*(元|块|预算)?").matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        Matcher kiloMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[kK]").matcher(text);
        if (kiloMatcher.find()) {
            return (int) Math.round(Double.parseDouble(kiloMatcher.group(1)) * 1000);
        }
        Integer chineseBudget = extractChineseBudget(text);
        if (chineseBudget != null) {
            return chineseBudget;
        }
        return null;
    }

    private boolean hasBudgetAmount(String text) {
        return extractExplicitBudget(text) != null;
    }

    private boolean containsChineseBudget(String text) {
        return extractChineseBudget(text) != null;
    }

    private Integer extractChineseBudget(String message) {
        String text = message == null ? "" : message;
        Matcher arabicWanQian = Pattern.compile("(\\d)万(\\d)(?:千)?(\\d)?(?:百)?").matcher(text);
        if (arabicWanQian.find()) {
            int total = Integer.parseInt(arabicWanQian.group(1)) * 10000
                    + Integer.parseInt(arabicWanQian.group(2)) * 1000;
            if (arabicWanQian.group(3) != null) {
                total += Integer.parseInt(arabicWanQian.group(3)) * 100;
            }
            return total;
        }
        Matcher chineseWanQian = Pattern.compile("([一二两三四五六七八九])万([一二两三四五六七八九])(?:千)?([一二两三四五六七八九])?(?:百)?").matcher(text);
        if (chineseWanQian.find()) {
            int total = chineseDigitValue(chineseWanQian.group(1)) * 10000
                    + chineseDigitValue(chineseWanQian.group(2)) * 1000;
            if (StringUtils.hasText(chineseWanQian.group(3))) {
                total += chineseDigitValue(chineseWanQian.group(3)) * 100;
            }
            return total;
        }
        Matcher decimalWan = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*万").matcher(text);
        if (decimalWan.find()) {
            return (int) Math.round(Double.parseDouble(decimalWan.group(1)) * 10000);
        }
        Matcher wanQian = Pattern.compile("(\\d)万(\\d)千?").matcher(text);
        if (wanQian.find()) {
            return Integer.parseInt(wanQian.group(1)) * 10000 + Integer.parseInt(wanQian.group(2)) * 1000;
        }
        if (containsAny(text, "一万五", "1万5", "1.5万", "15000")) {
            return 15000;
        }
        if (containsAny(text, "一万", "1万", "10000")) {
            return 10000;
        }
        if (containsAny(text, "两万", "二万", "2万", "20000")) {
            return 20000;
        }
        if (containsAny(text, "八千", "8000")) {
            return 8000;
        }
        if (containsAny(text, "六千", "6000")) {
            return 6000;
        }
        if (containsAny(text, "五千", "5000")) {
            return 5000;
        }
        return null;
    }

    private int chineseDigitValue(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> 0;
        };
    }

    private BuildPlan selectBuildPlan(int budget, String message, AiSettingsService.EffectiveAiSettings settings, UserMemory userMemory) {
        return selectBuildPlan(budget, message, settings, userMemory, null);
    }

    private BuildPlan selectBuildPlan(int budget, String message, AiSettingsService.EffectiveAiSettings settings, UserMemory userMemory, BuildProgressReporter progressReporter) {
        BuildPreferences preferences = buildPreferences(message, userMemory);
        Map<HardwarePartType, List<HardwarePart>> parts = new EnumMap<>(HardwarePartType.class);
        for (HardwarePart part : hardwarePartRepository.findByEnabledTrue()) {
            parts.computeIfAbsent(part.getType(), ignored -> new ArrayList<>()).add(part);
        }
        List<HardwarePart> cpus = includePreferredCandidates(prioritizeCandidates(required(parts, HardwarePartType.CPU), 18), required(parts, HardwarePartType.CPU), preferences, HardwarePartType.CPU, 20);
        List<HardwarePart> motherboards = includePreferredCandidates(prioritizeCandidates(required(parts, HardwarePartType.MOTHERBOARD), 16), required(parts, HardwarePartType.MOTHERBOARD), preferences, HardwarePartType.MOTHERBOARD, 18);
        List<HardwarePart> gpus = includePreferredCandidates(prioritizeCandidates(required(parts, HardwarePartType.GPU), 16), required(parts, HardwarePartType.GPU), preferences, HardwarePartType.GPU, 20);
        List<HardwarePart> memories = includePreferredCandidates(prioritizeCandidates(required(parts, HardwarePartType.MEMORY), 8), required(parts, HardwarePartType.MEMORY), preferences, HardwarePartType.MEMORY, 12);
        List<HardwarePart> storages = prioritizeCandidates(required(parts, HardwarePartType.STORAGE), 5);
        List<HardwarePart> psus = prioritizeCandidates(required(parts, HardwarePartType.PSU), 8);
        List<HardwarePart> cases = includePreferredCandidates(prioritizeCandidates(required(parts, HardwarePartType.CASE), 7), required(parts, HardwarePartType.CASE), preferences, HardwarePartType.CASE, 9);
        List<HardwarePart> coolers = automaticCoolerCandidates(includePreferredCandidates(prioritizeCandidates(required(parts, HardwarePartType.COOLER), 7), required(parts, HardwarePartType.COOLER), preferences, HardwarePartType.COOLER, 9));
        BuildPlan best = null;
        BuildPlan fallback = null;
        BuildPlan budgetTarget = null;
        List<BuildPlan> localCandidates = new ArrayList<>();
        Map<Long, BuildPartSelectionRequest> localSelections = Map.of();
        int maxLocalBudget = budget + budgetFlexRange(budget);
        int minTargetTotal = minimumTargetTotal(budget, preferences);
        for (int cpuIndex = 0; cpuIndex < cpus.size(); cpuIndex++) {
            HardwarePart cpu = cpus.get(cpuIndex);
            if (progressReporter != null && (cpuIndex == 0 || cpuIndex % 2 == 0)) {
                int progress = 24 + Math.min(9, Math.round((cpuIndex + 1) * 9f / Math.max(1, cpus.size())));
                progressReporter.update(progress, "筛选硬件", "正在缩小 CPU/主板/GPU 候选 " + (cpuIndex + 1) + "/" + cpus.size());
            }
            List<HardwarePart> compatibleMotherboards = compatibleMotherboardCandidates(cpu, motherboards, budget, preferences);
            for (HardwarePart motherboard : compatibleMotherboards) {
                List<HardwarePart> compatibleMemories = compatibleMemoryCandidates(motherboard, memories, budget, preferences);
                if (compatibleMemories.isEmpty()) {
                    continue;
                }
                List<HardwarePart> compatibleCases = topSupportingCandidates(cases.stream()
                        .filter(pcCase -> isCaseCompatible(motherboard, pcCase))
                        .toList(), 2, Math.max(260, Math.round(budget * 0.08f)), preferences);
                if (compatibleCases.isEmpty()) {
                    continue;
                }
                for (HardwarePart gpu : gpus) {
                    int requiredWattage = cpu.getWattage() + gpu.getWattage() + 120;
                    List<HardwarePart> compatiblePsus = topSupportingCandidates(psus.stream()
                            .filter(psu -> psu.getWattage() >= requiredWattage)
                            .toList(), supportingCandidateLimit(budget, 2, 3), Math.max(320, Math.round(budget * 0.08f)), preferences);
                    if (compatiblePsus.isEmpty()) {
                        continue;
                    }
                    List<HardwarePart> compatibleCoolers = compatibleCoolerCandidates(cpu, coolers, compatibleCases, budget, preferences);
                    if (compatibleCoolers.isEmpty()) {
                        continue;
                    }
                    List<HardwarePart> selectedStorages = storageCandidates(storages, budget, preferences);
                    for (HardwarePart memory : compatibleMemories) {
                        int corePrice = referencePrice(cpu) + referencePrice(motherboard) + referencePrice(gpu) + referencePrice(memory);
                        if (corePrice > maxLocalBudget) {
                            continue;
                        }
                        for (HardwarePart storage : selectedStorages) {
                            for (HardwarePart psu : compatiblePsus) {
                                    for (HardwarePart pcCase : compatibleCases) {
                                        for (HardwarePart cooler : compatibleCoolers) {
                                            if (!isCoolerChoiceAllowedForCase(pcCase, cooler, compatibleCoolers, cpu, preferences, budget)) {
                                                continue;
                                            }
                                            List<HardwarePart> selected = List.of(cpu, motherboard, gpu, memory, storage, psu, pcCase, cooler);
                                            List<BuildPartSelectionRequest> selections = selected.stream()
                                                .map(part -> selectionForPart(part, localSelections))
                                                .toList();
                                        BuildPlan plan = new BuildPlan(selected, selections, totalPrice(selected, selections), performanceScore(selected));
                                        rememberLocalCandidate(localCandidates, plan, budget, preferences, settings);
                                        if (isBudgetTargetPlan(plan, budget, minTargetTotal) && isBetterBudgetTargetPlan(plan, budgetTarget, budget, preferences, settings)) {
                                            budgetTarget = plan;
                                        }
                                        if (isBetterFallbackPlan(plan, fallback, budget, preferences, settings)) {
                                            fallback = plan;
                                        }
                                        if (plan.totalPrice() <= budget && isBetterPlan(plan, best, budget, preferences, settings)) {
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
        keepDiverseLocalCandidates(localCandidates, budget, preferences, settings);
        if (settings.requirePurchasableProducts()) {
            if (localCandidates.isEmpty()) {
                throw new BusinessException("硬件库数据不足，暂时无法自动生成配置单");
            }
            BuildPlan purchasableBest = null;
            BuildPlan purchasableFallback = null;
            BuildPlan purchasableBudgetTarget = null;
            int index = 0;
            for (BuildPlan candidate : localCandidates) {
                index++;
                Optional<BuildPlan> purchasablePlan = attachPurchasableProducts(
                        candidate,
                        preferences,
                        settings,
                        progressReporter,
                        index,
                        localCandidates.size()
                );
                if (purchasablePlan.isEmpty()) {
                    continue;
                }
                BuildPlan plan = purchasablePlan.get();
                if (isBetterFallbackPlan(plan, purchasableFallback, budget, preferences, settings)) {
                    purchasableFallback = plan;
                }
                if (plan.totalPrice() <= budget && isBetterPlan(plan, purchasableBest, budget, preferences, settings)) {
                    purchasableBest = plan;
                }
                if (isBudgetTargetPlan(plan, budget, minTargetTotal) && isBetterBudgetTargetPlan(plan, purchasableBudgetTarget, budget, preferences, settings)) {
                    purchasableBudgetTarget = plan;
                }
            }
            if (purchasableBudgetTarget != null) {
                return purchasableBudgetTarget;
            }
            if (purchasableBest != null) {
                if (isClearlyUnderTargetBudget(purchasableBest, minTargetTotal)
                        && !isPremiumCeilingBuild(purchasableBest, budget, preferences)) {
                    throw underusedBudgetException(budget, purchasableBest.totalPrice(), minTargetTotal);
                }
                return purchasableBest;
            }
            if (purchasableFallback != null) {
                int maxAcceptableOverBudget = budget + budgetFlexRange(budget);
                if (purchasableFallback.totalPrice() <= maxAcceptableOverBudget) {
                    return purchasableFallback;
                }
                throw new BusinessException("按当前京东可购买商品，完整主机最低可行方案约 ¥" + purchasableFallback.totalPrice()
                        + "，已经明显超过你的预算 ¥" + budget
                        + "。要达到这个用途需要加预算或降低帧率、画质、分辨率目标；我不会强行保存一套超预算太多的配置。");
            }
            throw new BusinessException("京东暂时没有找到完整可购买的配置单商品，请稍后重试或换个预算范围。");
        }
        if (budgetTarget != null) {
            return budgetTarget;
        }
        if (best != null) {
            if (isClearlyUnderTargetBudget(best, minTargetTotal)
                    && !isPremiumCeilingBuild(best, budget, preferences)) {
                throw underusedBudgetException(budget, best.totalPrice(), minTargetTotal);
            }
            return best;
        }
        if (fallback != null) {
            int maxAcceptableOverBudget = budget + budgetFlexRange(budget);
            if (fallback.totalPrice() <= maxAcceptableOverBudget) {
                return fallback;
            }
            throw new BusinessException("按当前京东可购买商品，完整主机最低可行方案约 ¥" + fallback.totalPrice()
                    + "，已经明显超过你的预算 ¥" + budget
                    + "。要达到这个用途需要加预算或降低帧率、画质、分辨率目标；我不会强行保存一套超预算太多的配置。");
        }
        throw new BusinessException("硬件库数据不足，暂时无法自动生成配置单");
    }

    private List<HardwarePart> compatibleMotherboardCandidates(
            HardwarePart cpu,
            List<HardwarePart> motherboards,
            int budget,
            BuildPreferences preferences
    ) {
        List<HardwarePart> compatible = motherboards.stream()
                .filter(motherboard -> same(cpu.getSocketType(), motherboard.getSocketType()))
                .filter(motherboard -> isMotherboardBalancedForCpu(cpu, motherboard))
                .toList();
        if (StringUtils.hasText(preferences.preferredMemoryType())) {
            List<HardwarePart> preferredMemoryTypeBoards = compatible.stream()
                    .filter(motherboard -> preferences.preferredMemoryType().equalsIgnoreCase(motherboard.getMemoryType()))
                    .toList();
            if (!preferredMemoryTypeBoards.isEmpty()) {
                compatible = preferredMemoryTypeBoards;
            }
        } else if (shouldPreferLegacyIntelDdr4(cpu, budget)) {
            List<HardwarePart> ddr4Boards = compatible.stream()
                    .filter(motherboard -> "DDR4".equalsIgnoreCase(motherboard.getMemoryType()))
                    .toList();
            if (!ddr4Boards.isEmpty()) {
                compatible = ddr4Boards;
            }
        }
        return topSupportingCandidates(compatible, supportingCandidateLimit(budget, 5, 6), Math.max(480, Math.round(budget * 0.14f)), preferences);
    }

    private List<HardwarePart> compatibleMemoryCandidates(
            HardwarePart motherboard,
            List<HardwarePart> memories,
            int budget,
            BuildPreferences preferences
    ) {
        List<HardwarePart> compatible = memories.stream()
                .filter(memory -> same(memory.getMemoryType(), motherboard.getMemoryType()))
                .toList();
        if (preferences.preferredMemoryCapacityGb() > 0) {
            List<HardwarePart> preferredCapacity = compatible.stream()
                    .filter(memory -> memoryCapacityGb(memory) == preferences.preferredMemoryCapacityGb())
                    .toList();
            if (!preferredCapacity.isEmpty()) {
                compatible = preferredCapacity;
            }
        } else if (shouldDefaultTo16GbMemory(budget)) {
            List<HardwarePart> valueCapacity = compatible.stream()
                    .filter(memory -> memoryCapacityGb(memory) > 0 && memoryCapacityGb(memory) <= 16)
                    .toList();
            if (!valueCapacity.isEmpty()) {
                compatible = valueCapacity;
            }
        }
        return topSupportingCandidates(compatible, supportingCandidateLimit(budget, 3, 4), Math.max(260, Math.round(budget * 0.08f)), preferences);
    }

    private List<HardwarePart> storageCandidates(List<HardwarePart> storages, int budget, BuildPreferences preferences) {
        List<HardwarePart> candidates = storages;
        if (budget < 10000) {
            List<HardwarePart> valueStorages = storages.stream()
                    .filter(storage -> {
                        String model = storage.getModel().toUpperCase(Locale.ROOT);
                        return model.contains("PCIE 4.0") && model.contains("1TB");
                    })
                    .toList();
            if (!valueStorages.isEmpty()) {
                candidates = valueStorages;
            }
        }
        return topSupportingCandidates(candidates, supportingCandidateLimit(budget, 2, 4), Math.max(280, Math.round(budget * 0.08f)), preferences);
    }

    private List<HardwarePart> automaticCoolerCandidates(List<HardwarePart> coolers) {
        List<HardwarePart> mainstream = coolers.stream()
                .filter(cooler -> !is280Aio(cooler))
                .toList();
        return mainstream.isEmpty() ? coolers : mainstream;
    }

    private List<HardwarePart> compatibleCoolerCandidates(
            HardwarePart cpu,
            List<HardwarePart> coolers,
            List<HardwarePart> compatibleCases,
            int budget,
            BuildPreferences preferences
    ) {
        List<HardwarePart> compatible = coolers.stream()
                .filter(cooler -> isCoolerAdequateForCpu(cpu, cooler))
                .toList();
        if (compatible.isEmpty()) {
            return List.of();
        }
        boolean canUse360 = compatibleCases.stream().anyMatch(this::caseLikelySupports360Aio)
                && compatible.stream().anyMatch(this::is360Aio);
        if (canUse360) {
            List<HardwarePart> preferred = compatible.stream()
                    .filter(cooler -> !isAioCooler(cooler) || is360Aio(cooler))
                    .toList();
            if (!preferred.isEmpty()) {
                compatible = preferred;
            }
            if (shouldPrefer360Aio(cpu, preferences, budget)) {
                List<HardwarePart> only360 = compatible.stream()
                        .filter(this::is360Aio)
                        .toList();
                if (!only360.isEmpty()) {
                    compatible = only360;
                }
            }
        }
        return topSupportingCandidates(compatible, supportingCandidateLimit(budget, 2, 3), Math.max(220, Math.round(budget * 0.06f)), preferences);
    }

    private int supportingCandidateLimit(int budget, int normalLimit, int highBudgetLimit) {
        if (budget >= 12000) {
            return highBudgetLimit;
        }
        if (budget >= 9000) {
            return Math.max(normalLimit, highBudgetLimit - 1);
        }
        return normalLimit;
    }

    private boolean shouldPreferLegacyIntelDdr4(HardwarePart cpu, int budget) {
        return isLegacyIntelCore(cpu) && budget < 10000;
    }

    private boolean isLegacyIntelCore(HardwarePart cpu) {
        if (cpu == null || cpu.getType() != HardwarePartType.CPU) {
            return false;
        }
        String model = cpu.getModel().toUpperCase(Locale.ROOT);
        return "LGA1700".equalsIgnoreCase(cpu.getSocketType())
                && model.contains("CORE")
                && !model.contains("CORE ULTRA");
    }

    private boolean shouldDefaultTo16GbMemory(int budget) {
        return budget < 10000;
    }

    private int memoryCapacityGb(HardwarePart memory) {
        if (memory == null || memory.getType() != HardwarePartType.MEMORY) {
            return 0;
        }
        Matcher matcher = Pattern.compile("(\\d{1,3})\\s*G(?:B)?", Pattern.CASE_INSENSITIVE).matcher(memory.getModel());
        if (!matcher.find()) {
            return 0;
        }
        return parseInteger(matcher.group(1));
    }

    private List<HardwarePart> topSupportingCandidates(
            List<HardwarePart> parts,
            int limit,
            int targetPrice,
            BuildPreferences preferences
    ) {
        if (parts.isEmpty()) {
            return List.of();
        }
        return parts.stream()
                .sorted(Comparator.comparingInt((HardwarePart part) -> supportingPartRank(part, targetPrice, preferences)).reversed()
                        .thenComparing(HardwarePart::getModel))
                .limit(limit)
                .toList();
    }

    private int supportingPartRank(HardwarePart part, int targetPrice, BuildPreferences preferences) {
        int price = referencePrice(part);
        int score = hardwareGenerationRank(part) * 2
                + part.getPerformanceScore() * 3
                - Math.abs(price - targetPrice) / 4
                - price / 28;
        if (StringUtils.hasText(preferences.preferredColor()) && isVisualPartTitle(part.getModel())) {
            String color = productColor(part.getModel());
            if (preferences.preferredColor().equals(color)) {
                score += 90;
            } else if (StringUtils.hasText(color)) {
                score -= 70;
            }
        }
        score += casePreferenceScore(part, preferences) * 80;
        score += coolerPreferenceScore(part, preferences) * 80;
        score += coolerMainstreamScore(part);
        return score;
    }

    private int coolerMainstreamScore(HardwarePart part) {
        if (part.getType() != HardwarePartType.COOLER) {
            return 0;
        }
        if (is360Aio(part)) {
            return 180;
        }
        if (is280Aio(part)) {
            return -260;
        }
        if (is240Aio(part)) {
            return -40;
        }
        String text = hardwarePartText(part);
        if (containsAny(text, "DUAL TOWER", "双塔")) {
            return 80;
        }
        return 0;
    }

    private int casePreferenceScore(HardwarePart part, BuildPreferences preferences) {
        if (part.getType() != HardwarePartType.CASE && part.getType() != HardwarePartType.MOTHERBOARD) {
            return 0;
        }
        int score = 0;
        String text = hardwarePartText(part);
        String size = preferences.preferredCaseSize();
        String formFactor = part.getFormFactor() == null ? "" : part.getFormFactor().toUpperCase(Locale.ROOT);
        if (StringUtils.hasText(size)) {
            if ("ATX".equals(size) && ("ATX".equals(formFactor) || "E-ATX".equals(formFactor))) {
                score += 3;
            } else if ("M-ATX".equals(size) && ("M-ATX".equals(formFactor) || "MATX".equals(formFactor))) {
                score += 4;
            } else if ("ITX".equals(size) && "ITX".equals(formFactor)) {
                score += 5;
            }
        }
        if (part.getType() == HardwarePartType.CASE) {
            String style = preferences.preferredCaseStyle();
            if ("PANORAMA".equals(style) && containsAny(text, "海景房", "全景", "270", "玻璃")) {
                score += 4;
            } else if ("TOWER".equals(style) && containsAny(text, "中塔", "风道", "网孔", "MESH", "AIRFLOW")) {
                score += 4;
            } else if ("SILENT".equals(style) && containsAny(text, "静音", "隔音", "低噪")) {
                score += 4;
            }
        }
        return score;
    }

    private int coolerPreferenceScore(HardwarePart part, BuildPreferences preferences) {
        if (part.getType() != HardwarePartType.COOLER) {
            return 0;
        }
        String text = hardwarePartText(part);
        String coolerType = preferences.preferredCoolerType();
        if ("AIO".equals(coolerType)) {
            if (is360Aio(part)) {
                return 8;
            }
            if (is280Aio(part)) {
                return -5;
            }
            if (is240Aio(part)) {
                return 2;
            }
            return containsAny(text, "AIO", "水冷") ? 1 : -4;
        }
        if ("AIR".equals(coolerType)) {
            return containsAny(text, "风冷", "塔式", "双塔", "TOWER", "LOW PROFILE") ? 5 : text.contains("水冷") || text.contains("AIO") ? -5 : 0;
        }
        if ("QUIET".equals(coolerType)) {
            return containsAny(text, "静音", "安静", "低噪", "PWM") ? 4 : 0;
        }
        return 0;
    }

    private boolean isCaseCompatible(HardwarePart motherboard, HardwarePart pcCase) {
        return !StringUtils.hasText(motherboard.getFormFactor())
                || !StringUtils.hasText(pcCase.getSupportedFormFactors())
                || pcCase.getSupportedFormFactors().toUpperCase(Locale.ROOT).contains(motherboard.getFormFactor().toUpperCase(Locale.ROOT));
    }

    private void rememberLocalCandidate(
            List<BuildPlan> candidates,
            BuildPlan plan,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        int maxLocalBudget = budget + budgetFlexRange(budget);
        if (plan.totalPrice() > maxLocalBudget) {
            return;
        }
        candidates.add(plan);
        int pruneThreshold = localCandidatePoolSize(budget) * 8;
        if (candidates.size() > pruneThreshold) {
            keepDiverseLocalCandidates(candidates, budget, preferences, settings);
        }
    }

    private int buildPlanRank(
            BuildPlan plan,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        int overBudget = Math.max(0, plan.totalPrice() - budget);
        int distance = Math.abs(budget - plan.totalPrice());
        return plan.performanceScore() * 10
                + explicitPreferenceScore(plan.parts(), preferences) * 12
                + memoryCapacityScore(plan.parts(), preferences)
                + colorHarmonyScore(plan.selections(), preferences, settings)
                - distance / 70
                - overBudget / 12
                - budgetUnderusePenalty(plan.totalPrice(), budget, preferences);
    }

    private int budgetFlexRange(int budget) {
        if (budget >= 25000) {
            return Math.max(2500, Math.min(4000, Math.round(budget * 0.09f)));
        }
        if (budget >= 12000) {
            return Math.max(1200, Math.min(1800, Math.round(budget * 0.11f)));
        }
        if (budget >= 8000) {
            return Math.max(900, Math.min(1400, Math.round(budget * 0.13f)));
        }
        return Math.max(600, Math.min(1200, Math.round(budget * 0.18f)));
    }

    private int minimumTargetTotal(int budget, BuildPreferences preferences) {
        if (!preferences.useBudgetFloor()) {
            return 0;
        }
        if (budget >= 15000) {
            return Math.round(budget * 0.85f);
        }
        if (budget >= 10000) {
            return Math.round(budget * 0.82f);
        }
        if (budget >= 8000) {
            return Math.round(budget * 0.85f);
        }
        return 0;
    }

    private int budgetUnderusePenalty(int totalPrice, int budget, BuildPreferences preferences) {
        int minTargetTotal = minimumTargetTotal(budget, preferences);
        if (minTargetTotal <= 0 || totalPrice >= minTargetTotal) {
            return 0;
        }
        return 700 + (minTargetTotal - totalPrice) / 2;
    }

    private boolean isBudgetTargetPlan(BuildPlan plan, int budget, int minTargetTotal) {
        return minTargetTotal > 0
                && plan.totalPrice() >= minTargetTotal
                && plan.totalPrice() <= budget + budgetFlexRange(budget);
    }

    private boolean isClearlyUnderTargetBudget(BuildPlan plan, int minTargetTotal) {
        return minTargetTotal > 0 && plan.totalPrice() < minTargetTotal;
    }

    private boolean isPremiumCeilingBuild(BuildPlan plan, int budget, BuildPreferences preferences) {
        if (budget < 35000 || plan == null) {
            return false;
        }
        boolean flagshipGpu = plan.parts().stream()
                .filter(part -> part.getType() == HardwarePartType.GPU)
                .map(part -> part.getModel().toUpperCase(Locale.ROOT))
                .anyMatch(model -> model.contains("RTX 5090") || model.contains("RTX 5080") || model.contains("RX 9070 XT"));
        boolean premiumCpu = plan.parts().stream()
                .filter(part -> part.getType() == HardwarePartType.CPU)
                .map(part -> part.getModel().toUpperCase(Locale.ROOT))
                .anyMatch(model -> model.contains("9950X3D")
                        || model.contains("9950X3D2")
                        || model.contains("9900X3D")
                        || model.contains("9850X3D")
                        || model.contains("9800X3D")
                        || model.contains("CORE ULTRA 9"));
        return flagshipGpu && premiumCpu && plan.totalPrice() >= Math.round(budget * 0.72f);
    }

    private BusinessException underusedBudgetException(int budget, int bestTotal, int minTargetTotal) {
        return new BusinessException("当前只找到约 ¥" + bestTotal
                + " 的完整可购买方案，距离你的预算 ¥" + budget
                + " 太远。为避免把明显低一档的配置当成预算方案保存，我没有强行保存；请稍后重试，或指定显卡/CPU 档位、放宽配色和京东库存限制。");
    }

    private void keepDiverseLocalCandidates(
            List<BuildPlan> candidates,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        int targetSize = localCandidatePoolSize(budget);
        if (candidates.size() <= targetSize) {
            return;
        }
        List<BuildPlan> ranked = candidates.stream()
                .sorted((left, right) -> Integer.compare(
                        buildPlanRank(right, budget, preferences, settings),
                        buildPlanRank(left, budget, preferences, settings)
                ))
                .toList();
        List<BuildPlan> retained = new ArrayList<>();
        int minTargetTotal = minimumTargetTotal(budget, preferences);
        ranked.stream()
                .filter(plan -> isBudgetTargetPlan(plan, budget, minTargetTotal))
                .sorted((left, right) -> compareBudgetTargetPlans(left, right, budget, preferences, settings))
                .limit(Math.max(18, targetSize / 2))
                .forEach(plan -> addUniquePlan(retained, plan, targetSize));

        Map<String, Integer> cpuCounts = new LinkedHashMap<>();
        Map<String, Integer> gpuCounts = new LinkedHashMap<>();
        for (BuildPlan plan : ranked) {
            String cpuKey = partModelKey(plan, HardwarePartType.CPU);
            String gpuKey = partModelKey(plan, HardwarePartType.GPU);
            if (cpuCounts.getOrDefault(cpuKey, 0) >= 4 || gpuCounts.getOrDefault(gpuKey, 0) >= 6) {
                continue;
            }
            addUniquePlan(retained, plan, targetSize);
            cpuCounts.merge(cpuKey, 1, Integer::sum);
            gpuCounts.merge(gpuKey, 1, Integer::sum);
            if (retained.size() >= Math.max(24, targetSize * 3 / 4)) {
                break;
            }
        }

        ranked.stream()
                .sorted(Comparator.comparingInt(BuildPlan::performanceScore).reversed()
                        .thenComparing((left, right) -> Integer.compare(
                                buildPlanRank(right, budget, preferences, settings),
                                buildPlanRank(left, budget, preferences, settings)
                        )))
                .limit(Math.max(10, targetSize / 5))
                .forEach(plan -> addUniquePlan(retained, plan, targetSize));

        candidates.stream()
                .sorted(Comparator.comparingInt(BuildPlan::totalPrice)
                        .thenComparing(Comparator.comparingInt(BuildPlan::performanceScore).reversed()))
                .limit(Math.max(8, targetSize / 8))
                .forEach(plan -> addUniquePlan(retained, plan, targetSize));

        for (BuildPlan plan : ranked) {
            if (retained.size() >= targetSize) {
                break;
            }
            addUniquePlan(retained, plan, targetSize);
        }
        candidates.clear();
        candidates.addAll(retained);
    }

    private int localCandidatePoolSize(int budget) {
        if (budget >= 12000) {
            return 64;
        }
        if (budget >= 9000) {
            return 56;
        }
        if (budget >= 7000) {
            return 48;
        }
        return 40;
    }

    private int compareBudgetTargetPlans(
            BuildPlan left,
            BuildPlan right,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        boolean leftWithin = left.totalPrice() <= budget;
        boolean rightWithin = right.totalPrice() <= budget;
        if (leftWithin != rightWithin) {
            return leftWithin ? -1 : 1;
        }
        int qualityDiff = Integer.compare(
                planQualityScore(right, budget, preferences, settings),
                planQualityScore(left, budget, preferences, settings)
        );
        if (qualityDiff != 0) {
            return qualityDiff;
        }
        return Integer.compare(Math.abs(left.totalPrice() - budget), Math.abs(right.totalPrice() - budget));
    }

    private void addUniquePlan(List<BuildPlan> plans, BuildPlan plan, int targetSize) {
        if (plans.size() < targetSize && !plans.contains(plan)) {
            plans.add(plan);
        }
    }

    private String partModelKey(BuildPlan plan, HardwarePartType type) {
        return plan.parts().stream()
                .filter(part -> part.getType() == type)
                .map(HardwarePart::getModel)
                .findFirst()
                .orElse("");
    }

    private int memoryCapacityScore(List<HardwarePart> parts, BuildPreferences preferences) {
        if (preferences.preferredMemoryCapacityGb() <= 0) {
            return 0;
        }
        for (HardwarePart part : parts) {
            if (part.getType() != HardwarePartType.MEMORY) {
                continue;
            }
            String model = part.getModel().toUpperCase(Locale.ROOT);
            if (model.contains(preferences.preferredMemoryCapacityGb() + "GB")
                    || model.contains(preferences.preferredMemoryCapacityGb() + "G")) {
                return 18;
            }
        }
        return 0;
    }

    private Optional<BuildPlan> attachPurchasableProducts(
            BuildPlan candidate,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings,
            BuildProgressReporter progressReporter,
            int candidateIndex,
            int candidateCount
    ) {
        Map<Long, BuildPartSelectionRequest> productSelections = new LinkedHashMap<>();
        List<HardwarePart> parts = new ArrayList<>(candidate.parts());
        for (int index = 0; index < parts.size(); index++) {
            HardwarePart part = parts.get(index);
            if (productSelections.containsKey(part.getId())) {
                continue;
            }
            if (progressReporter != null) {
                int progress = 34 + Math.min(52, Math.round(((candidateIndex - 1) * parts.size() + index + 1) * 52f / Math.max(1, candidateCount * parts.size())));
                progressReporter.update(
                        progress,
                        "检索京东商品",
                        "候选 " + candidateIndex + "/" + candidateCount + " · " + hardwareTypeLabel(part.getType()) + " " + part.getModel()
                );
            }
            List<JdProduct> products = jdProductSearchService.availableProductsForPart(
                    part,
                    preferences.preferredColor(),
                    part.getType() == HardwarePartType.CPU,
                    preferences.preferredCaseStyle(),
                    preferences.preferredCoolerType()
            );
            boolean attached = false;
            for (JdProduct item : products) {
                if (part.getType() == HardwarePartType.CPU && isCpuMotherboardBundle(item.title())) {
                    Optional<HardwarePart> motherboard = resolveBundledMotherboard(item.title(), part, parts);
                    if (motherboard.isPresent()) {
                        replaceSelectedPart(parts, motherboard.get());
                        putCpuMotherboardBundleSelections(productSelections, part, motherboard.get(), item);
                        attached = true;
                        break;
                    }
                    continue;
                }
                productSelections.put(part.getId(), new BuildPartSelectionRequest(
                        part.getId(),
                        item.title(),
                        item.price(),
                        item.image(),
                        item.link(),
                        item.skuId(),
                        StringUtils.hasText(item.matchedModel()) ? item.matchedModel() : part.getModel()
                ));
                attached = true;
                break;
            }
            if (!attached) {
                log.info("Reject JD purchasable candidate {}/{}: no compatible product for {} {}",
                        candidateIndex,
                        candidateCount,
                        hardwareTypeLabel(part.getType()),
                        part.getModel());
                return Optional.empty();
            }
        }
        List<BuildPartSelectionRequest> selections = parts.stream()
                .map(part -> selectionForPart(part, productSelections))
                .toList();
        if (hasStrictColorConflict(selections, preferences, settings)) {
            log.info("Reject JD purchasable candidate {}/{}: strict color conflict for preferred color {}",
                    candidateIndex,
                    candidateCount,
                    preferences.preferredColor());
            return Optional.empty();
        }
        return Optional.of(new BuildPlan(
                parts,
                selections,
                totalPrice(parts, selections),
                performanceScore(parts)
        ));
    }

    private Optional<HardwarePart> selectedPart(List<HardwarePart> parts, HardwarePartType type) {
        return parts.stream().filter(part -> part.getType() == type).findFirst();
    }

    private void replaceSelectedPart(List<HardwarePart> parts, HardwarePart replacement) {
        for (int index = 0; index < parts.size(); index++) {
            if (parts.get(index).getType() == replacement.getType()) {
                parts.set(index, replacement);
                return;
            }
        }
    }

    private boolean isCpuMotherboardBundle(String title) {
        String text = title == null ? "" : title;
        String normalizedTitle = text.toUpperCase(Locale.ROOT);
        return text.contains("主板CPU套装")
                || text.contains("主板+CPU")
                || text.contains("CPU+主板")
                || text.contains("板U")
                || text.contains("板u")
                || text.contains("主板套装")
                || (text.contains("主板") && text.contains("CPU") && text.contains("套装"))
                || (text.contains("搭") && text.contains("主板"))
                || (normalizedTitle.contains("CPU+") && BOARD_CHIPSET_PATTERN.matcher(normalizedTitle).find());
    }

    private boolean bundleMentionsMotherboard(String title, HardwarePart motherboard) {
        String chipset = extractBoardChipset(motherboard.getModel());
        if (StringUtils.hasText(chipset)) {
            List<String> titleChipsets = extractBoardChipsets(title);
            if (!titleChipsets.isEmpty()) {
                String selectedVariantChipset = titleChipsets.get(titleChipsets.size() - 1);
                return selectedVariantChipset.equalsIgnoreCase(chipset);
            }
        }
        String compactTitle = compactText(title);
        String compactModel = compactText(motherboard.getModel());
        if (StringUtils.hasText(compactModel) && compactTitle.contains(compactModel)) {
            return true;
        }
        return StringUtils.hasText(chipset) && compactTitle.contains(chipset);
    }

    private Optional<HardwarePart> resolveBundledMotherboard(String title, HardwarePart cpu, List<HardwarePart> parts) {
        Optional<HardwarePart> selectedMotherboard = selectedPart(parts, HardwarePartType.MOTHERBOARD);
        if (selectedMotherboard.isPresent() && bundleMentionsMotherboard(title, selectedMotherboard.get())) {
            return selectedMotherboard;
        }
        List<String> titleChipsets = extractBoardChipsets(title);
        String selectedBundleChipset = titleChipsets.isEmpty() ? "" : titleChipsets.get(titleChipsets.size() - 1);
        if (!StringUtils.hasText(selectedBundleChipset)) {
            return Optional.empty();
        }
        Optional<HardwarePart> selectedMemory = selectedPart(parts, HardwarePartType.MEMORY);
        Optional<HardwarePart> selectedCase = selectedPart(parts, HardwarePartType.CASE);
        return hardwarePartRepository.findByEnabledTrue().stream()
                .filter(part -> part.getType() == HardwarePartType.MOTHERBOARD)
                .filter(board -> same(cpu.getSocketType(), board.getSocketType()))
                .filter(board -> selectedBundleChipset.equalsIgnoreCase(extractBoardChipset(board.getModel())))
                .filter(board -> selectedMemory.isEmpty() || same(selectedMemory.get().getMemoryType(), board.getMemoryType()))
                .filter(board -> selectedCase.isEmpty() || isCaseCompatible(board, selectedCase.get()))
                .filter(board -> isMotherboardBalancedForCpu(cpu, board))
                .sorted(Comparator.comparingInt((HardwarePart board) -> bundleMotherboardMatchScore(title, board, selectedMotherboard.orElse(null))).reversed()
                        .thenComparing(Comparator.comparingInt(this::hardwareGenerationRank).reversed())
                        .thenComparing(HardwarePart::getModel))
                .findFirst();
    }

    private int bundleMotherboardMatchScore(String title, HardwarePart board, HardwarePart selectedMotherboard) {
        int score = 0;
        String compactTitle = compactText(title);
        if (compactTitle.contains(compactText(board.getModel()))) {
            score += 20;
        }
        if (selectedMotherboard != null && same(selectedMotherboard.getFormFactor(), board.getFormFactor())) {
            score += 8;
        }
        String chipset = extractBoardChipset(board.getModel());
        if (StringUtils.hasText(chipset) && compactTitle.contains(chipset)) {
            score += 5;
        }
        return score;
    }

    private void putCpuMotherboardBundleSelections(
            Map<Long, BuildPartSelectionRequest> productSelections,
            HardwarePart cpu,
            HardwarePart motherboard,
            JdProduct item
    ) {
        int bundlePrice = item.price() == null ? 0 : item.price();
        productSelections.put(cpu.getId(), new BuildPartSelectionRequest(
                cpu.getId(),
                item.title() + "（板U套装）",
                bundlePrice,
                item.image(),
                item.link(),
                item.skuId(),
                StringUtils.hasText(item.matchedModel()) ? item.matchedModel() : cpu.getModel()
        ));
        productSelections.put(motherboard.getId(), new BuildPartSelectionRequest(
                motherboard.getId(),
                "已包含在 CPU 板U套装中：" + motherboard.getModel(),
                0,
                "",
                "",
                "",
                motherboard.getModel()
        ));
    }

    private String compactText(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
    }

    private String extractBoardChipset(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        Matcher matcher = BOARD_CHIPSET_PATTERN.matcher(value.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : "";
    }

    private List<String> extractBoardChipsets(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> chipsets = new ArrayList<>();
        Matcher matcher = BOARD_CHIPSET_PATTERN.matcher(value.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            chipsets.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return chipsets;
    }

    private List<HardwarePart> purchasableCandidates(List<HardwarePart> parts, Map<Long, BuildPartSelectionRequest> selections, HardwarePartType type) {
        List<HardwarePart> result = parts.stream()
                .filter(part -> hasAvailableSelection(selections.get(part.getId())))
                .toList();
        if (result.isEmpty()) {
            throw new BusinessException("京东暂时没有找到可购买的 " + hardwareTypeLabel(type) + " 商品，请稍后重试或换个预算范围。");
        }
        return result;
    }

    private BuildPreferences buildPreferences(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean productivity = containsAny(text, "生产力", "剪辑", "渲染", "建模", "设计", "cuda", "ai", "深度学习", "视频");
        boolean gaming = containsAny(text, "游戏", "主玩", "电竞", "网游", "单机", "3a", "打游戏")
                || StringUtils.hasText(detectGameName(text));
        boolean explicitAmdGpu = containsAny(text, "amd显卡", "a卡", "radeon", "rx ");
        boolean explicitNvidiaGpu = containsAny(text, "nvidia", "英伟达", "n卡", "rtx", "cuda", "nvenc");
        boolean explicitGpuPlatform = explicitAmdGpu || explicitNvidiaGpu;
        boolean explicitAmdCpu = containsAny(text, "amd cpu", "amd处理器", "amd平台", "锐龙", "ryzen")
                || (containsAny(text, "cpu", "处理器") && containsAny(text, "更倾向 amd", "倾向 amd", "偏向 amd", "选 amd"));
        boolean explicitIntelCpu = containsAny(text, "intel cpu", "intel处理器", "intel平台", "英特尔", "酷睿", "core ultra", "core i")
                || (containsAny(text, "cpu", "处理器") && containsAny(text, "更倾向 intel", "倾向 intel", "偏向 intel", "选 intel"));
        String color = "";
        if (containsAny(text, "白色", "纯白", "white")) {
            color = "WHITE";
        } else if (containsAny(text, "黑色", "全黑", "black")) {
            color = "BLACK";
        }
        boolean explicitColorPreference = StringUtils.hasText(color);
        String gpuModel = extractPreferredGpuModel(text);
        int memoryCapacity = extractPreferredMemoryCapacity(text);
        String memoryType = extractPreferredMemoryType(text);
        boolean gameFocused = gaming && !productivity;
        boolean preferX3d = text.contains("x3d") || (gameFocused && !explicitIntelCpu);
        boolean defaultNvidiaGpuBias = gameFocused && !explicitGpuPlatform;
        String caseSize = preferredCaseSizeFromText(text);
        String caseStyle = preferredCaseStyleFromText(text);
        String coolerType = preferredCoolerTypeFromText(text);
        boolean useBudgetFloor = hasBudgetAmount(text) && !isBudgetCeilingOnlyRequest(text);
        return new BuildPreferences(
                (productivity || explicitNvidiaGpu) && !explicitAmdGpu,
                explicitAmdGpu,
                explicitAmdCpu && !explicitIntelCpu,
                explicitIntelCpu && !explicitAmdCpu,
                color,
                gpuModel,
                memoryType,
                memoryCapacity,
                preferX3d,
                caseSize,
                caseStyle,
                coolerType,
                useBudgetFloor,
                gaming,
                productivity,
                explicitColorPreference,
                defaultNvidiaGpuBias
        );
    }

    private boolean isBudgetCeilingOnlyRequest(String text) {
        return containsAny(text,
                "预算上限", "最高预算", "最多", "不超过", "别超", "不要超", "控制在",
                "尽量便宜", "越便宜越好", "尽量省", "省钱", "能省则省",
                "够用就行", "够用就好", "压预算", "降预算");
    }

    private BuildPreferences buildPreferences(String message, UserMemory userMemory) {
        BuildPreferences current = buildPreferences(message);
        if (userMemory == null || !userMemory.hasAnyMemory()) {
            return current;
        }
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean explicitGpuPlatform = containsAny(text, "amd显卡", "a卡", "radeon", "rx ", "nvidia", "英伟达", "n卡", "rtx", "cuda", "nvenc");
        boolean explicitCpuPlatform = containsAny(text, "amd cpu", "amd处理器", "amd平台", "锐龙", "ryzen", "intel cpu", "intel处理器", "intel平台", "英特尔", "酷睿", "core ultra", "core i")
                || (containsAny(text, "cpu", "处理器") && containsAny(text, "amd", "intel"));
        boolean preferNvidiaGpu = current.preferNvidiaGpu();
        boolean preferAmdGpu = current.preferAmdGpu();
        boolean preferAmdCpu = current.preferAmdCpu();
        boolean preferIntelCpu = current.preferIntelCpu();
        boolean defaultNvidiaGpuBias = current.defaultNvidiaGpuBias();
        if (!explicitGpuPlatform && !preferNvidiaGpu && !preferAmdGpu) {
            String gpuMemory = userMemory.getGpuPlatformPreference().toLowerCase(Locale.ROOT);
            if (containsAny(gpuMemory, "nvidia", "cuda", "英伟达")) {
                preferNvidiaGpu = true;
                defaultNvidiaGpuBias = false;
            } else if (containsAny(gpuMemory, "amd", "radeon")) {
                preferAmdGpu = true;
                defaultNvidiaGpuBias = false;
            }
        }
        if (!explicitCpuPlatform && !preferAmdCpu && !preferIntelCpu) {
            String cpuMemory = userMemory.getCpuPlatformPreference().toLowerCase(Locale.ROOT);
            if (containsAny(cpuMemory, "amd", "ryzen", "锐龙")) {
                preferAmdCpu = true;
            } else if (containsAny(cpuMemory, "intel", "英特尔", "酷睿")) {
                preferIntelCpu = true;
            }
        }
        String preferredColor = current.preferredColor();
        if (!StringUtils.hasText(preferredColor)) {
            preferredColor = preferredColorFromMemory(userMemory);
        }
        String preferredCaseSize = current.preferredCaseSize();
        String preferredCaseStyle = current.preferredCaseStyle();
        String preferredCoolerType = current.preferredCoolerType();
        String preferredMemoryType = current.preferredMemoryType();
        String appearanceMemory = userMemory.getAppearancePreference().toLowerCase(Locale.ROOT);
        String noiseMemory = userMemory.getNoisePreference().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(preferredCaseSize)) {
            preferredCaseSize = preferredCaseSizeFromText(appearanceMemory);
        }
        if (!StringUtils.hasText(preferredCaseStyle)) {
            preferredCaseStyle = preferredCaseStyleFromText(appearanceMemory);
        }
        if (!StringUtils.hasText(preferredCoolerType)) {
            preferredCoolerType = preferredCoolerTypeFromText(appearanceMemory + " " + noiseMemory);
        }
        boolean preferX3d = current.preferX3dCpu();
        return new BuildPreferences(
                preferNvidiaGpu,
                preferAmdGpu,
                preferAmdCpu,
                preferIntelCpu,
                preferredColor,
                current.preferredGpuModel(),
                preferredMemoryType,
                current.preferredMemoryCapacityGb(),
                preferX3d,
                preferredCaseSize,
                preferredCaseStyle,
                preferredCoolerType,
                current.useBudgetFloor(),
                current.gamingUse(),
                current.productivityUse(),
                current.explicitColorPreference(),
                defaultNvidiaGpuBias
        );
    }

    private String preferredColorFromMemory(UserMemory userMemory) {
        String appearance = userMemory.getAppearancePreference().toLowerCase(Locale.ROOT);
        if (containsAny(appearance, "白色", "纯白", "white")) {
            return "WHITE";
        }
        if (containsAny(appearance, "黑色", "全黑", "black")) {
            return "BLACK";
        }
        return "";
    }

    private String extractPreferredMemoryType(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(value, "ddr4", "d4内存", "四代内存")) {
            return "DDR4";
        }
        if (containsAny(value, "ddr5", "d5内存", "五代内存")) {
            return "DDR5";
        }
        return "";
    }

    private String preferredCaseSizeFromText(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(value, "itx", "小 itx", "小机箱", "迷你", "小钢炮")) {
            return "ITX";
        }
        if (containsAny(value, "m-atx", "matx", "中 m-atx", "中等", "中机箱")) {
            return "M-ATX";
        }
        if (containsAny(value, "e-atx", "大 atx", "大机箱", "全塔", "大塔")) {
            return "ATX";
        }
        return "";
    }

    private String preferredCaseStyleFromText(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(value, "海景房", "全景", "270°", "270度", "双面玻璃")) {
            return "PANORAMA";
        }
        if (containsAny(value, "静音", "隔音", "安静")) {
            return "SILENT";
        }
        if (containsAny(value, "中塔", "风道", "网孔", "mesh", "airflow")) {
            return "TOWER";
        }
        return "";
    }

    private String preferredCoolerTypeFromText(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(value, "水冷", "一体水", "aio")) {
            return "AIO";
        }
        if (containsAny(value, "风冷", "塔式", "双塔", "下压")) {
            return "AIR";
        }
        if (containsAny(value, "静音", "安静", "低噪")) {
            return "QUIET";
        }
        return "";
    }

    private String extractPreferredGpuModel(String text) {
        Matcher rtxMatcher = Pattern.compile("\\b(?:rtx\\s*)?(50[6789]0|4090|4080|4070|4060)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (rtxMatcher.find()) {
            return "RTX " + rtxMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher rxMatcher = Pattern.compile("\\brx\\s*(9\\d{3}|7\\d{3}|6\\d{3})\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (rxMatcher.find()) {
            return "RX " + rxMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private int extractPreferredMemoryCapacity(String text) {
        Matcher beforeMatcher = Pattern.compile("(\\d{2,3})\\s*g(?:b)?\\s*内存", Pattern.CASE_INSENSITIVE).matcher(text);
        if (beforeMatcher.find()) {
            return parseInteger(beforeMatcher.group(1));
        }
        Matcher afterMatcher = Pattern.compile("内存[^\\d]{0,8}(\\d{2,3})\\s*g(?:b)?", Pattern.CASE_INSENSITIVE).matcher(text);
        if (afterMatcher.find()) {
            return parseInteger(afterMatcher.group(1));
        }
        return 0;
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isMotherboardBalancedForCpu(HardwarePart cpu, HardwarePart motherboard) {
        CpuTier cpuTier = cpuTier(cpu);
        BoardTier boardTier = boardTier(motherboard);
        return switch (cpuTier) {
            case ENTRY -> boardTier == BoardTier.ENTRY || boardTier == BoardTier.MAINSTREAM;
            case MAINSTREAM -> boardTier != BoardTier.ENTHUSIAST;
            case PERFORMANCE -> boardTier != BoardTier.ENTRY;
            case FLAGSHIP -> boardTier == BoardTier.PERFORMANCE || boardTier == BoardTier.ENTHUSIAST;
        };
    }

    private boolean isCoolerAdequateForCpu(HardwarePart cpu, HardwarePart cooler) {
        CpuTier cpuTier = cpuTier(cpu);
        boolean aio = isAioCooler(cooler);
        boolean strongAir = isStrongAirCooler(cooler);
        if (cpuTier == CpuTier.FLAGSHIP || cpu.getWattage() >= 170) {
            return aio;
        }
        if (cpuTier == CpuTier.PERFORMANCE || cpu.getWattage() >= 125) {
            return aio || strongAir;
        }
        return true;
    }

    private boolean isCoolerChoiceAllowedForCase(
            HardwarePart pcCase,
            HardwarePart cooler,
            List<HardwarePart> compatibleCoolers,
            HardwarePart cpu,
            BuildPreferences preferences,
            int budget
    ) {
        if (is280Aio(cooler)) {
            return false;
        }
        if (!caseLikelySupports360Aio(pcCase) || compatibleCoolers.stream().noneMatch(this::is360Aio)) {
            return true;
        }
        if (isAioCooler(cooler) && !is360Aio(cooler)) {
            return false;
        }
        return !shouldPrefer360Aio(cpu, preferences, budget) || is360Aio(cooler);
    }

    private boolean shouldPrefer360Aio(HardwarePart cpu, BuildPreferences preferences, int budget) {
        if ("AIR".equals(preferences.preferredCoolerType())) {
            return false;
        }
        return "AIO".equals(preferences.preferredCoolerType())
                || cpuTier(cpu) == CpuTier.FLAGSHIP
                || cpu.getWattage() >= 170
                || (budget >= 12000 && cpuTier(cpu) == CpuTier.PERFORMANCE);
    }

    private boolean caseLikelySupports360Aio(HardwarePart pcCase) {
        String text = ((pcCase.getModel() == null ? "" : pcCase.getModel()) + " "
                + (pcCase.getSpecs() == null ? "" : pcCase.getSpecs())).toUpperCase(Locale.ROOT);
        String formFactor = pcCase.getFormFactor() == null ? "" : pcCase.getFormFactor().toUpperCase(Locale.ROOT);
        return containsAny(text, "360", "E-ATX", "EATX", "全塔", "大中塔", "大机箱")
                || "E-ATX".equals(formFactor)
                || "ATX".equals(formFactor);
    }

    private boolean isAioCooler(HardwarePart cooler) {
        String text = hardwarePartText(cooler);
        return containsAny(text, "AIO", "水冷", "一体水", "360", "280", "240");
    }

    private boolean is360Aio(HardwarePart cooler) {
        String text = hardwarePartText(cooler);
        return isAioCooler(cooler) && containsAny(text, "360");
    }

    private boolean is280Aio(HardwarePart cooler) {
        String text = hardwarePartText(cooler);
        return isAioCooler(cooler) && containsAny(text, "280");
    }

    private boolean is240Aio(HardwarePart cooler) {
        String text = hardwarePartText(cooler);
        return isAioCooler(cooler) && containsAny(text, "240");
    }

    private boolean isStrongAirCooler(HardwarePart cooler) {
        String text = hardwarePartText(cooler);
        return containsAny(text, "DUAL TOWER", "双塔");
    }

    private CpuTier cpuTier(HardwarePart cpu) {
        String model = cpu.getModel().toUpperCase(Locale.ROOT);
        if (containsAny(model, "CORE ULTRA 3", "CORE I3", "I3-", "RYZEN 3")) {
            return CpuTier.ENTRY;
        }
        if (containsAny(model, "CORE ULTRA 9", "CORE I9", "I9-", "RYZEN 9")) {
            return CpuTier.FLAGSHIP;
        }
        if (containsAny(model, "CORE ULTRA 7", "CORE I7", "I7-", "RYZEN 7")) {
            return cpu.getWattage() >= 170 ? CpuTier.FLAGSHIP : CpuTier.PERFORMANCE;
        }
        if (containsAny(model, "CORE ULTRA 5", "CORE I5", "I5-", "RYZEN 5")) {
            boolean unlocked = containsAny(model, "KF", "K PLUS", "X3D") || model.endsWith("K") || model.endsWith("X");
            return unlocked || cpu.getWattage() >= 125 ? CpuTier.PERFORMANCE : CpuTier.MAINSTREAM;
        }
        if (cpu.getWattage() >= 170) {
            return CpuTier.FLAGSHIP;
        }
        if (cpu.getWattage() >= 125) {
            return CpuTier.PERFORMANCE;
        }
        return CpuTier.MAINSTREAM;
    }

    private BoardTier boardTier(HardwarePart motherboard) {
        String model = motherboard.getModel().toUpperCase(Locale.ROOT);
        if (model.contains("Z890") || model.contains("X870") || model.contains("X670E")) {
            return BoardTier.ENTHUSIAST;
        }
        if (model.contains("B650E") || model.contains("B850") || model.contains("B860") || model.contains("X670") || model.contains("Z790")) {
            return BoardTier.PERFORMANCE;
        }
        if (model.startsWith("H") || model.startsWith("A")) {
            return BoardTier.ENTRY;
        }
        return BoardTier.MAINSTREAM;
    }

    private boolean hasStrictColorConflict(
            List<BuildPartSelectionRequest> selections,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        if (settings.colorMatchMode() != AiColorMatchMode.STRICT || !StringUtils.hasText(preferences.preferredColor())) {
            return false;
        }
        for (BuildPartSelectionRequest selection : selections) {
            if (selection == null || !StringUtils.hasText(selection.title())) {
                continue;
            }
            String color = productColor(selection.title());
            if (StringUtils.hasText(color) && !preferences.preferredColor().equals(color)) {
                return true;
            }
        }
        return false;
    }

    private int colorHarmonyScore(List<BuildPartSelectionRequest> selections, BuildPreferences preferences, AiSettingsService.EffectiveAiSettings settings) {
        if (settings.colorMatchMode() == AiColorMatchMode.FLEXIBLE || !StringUtils.hasText(preferences.preferredColor())) {
            return 0;
        }
        String targetColor = preferences.preferredColor();
        int score = 0;
        for (BuildPartSelectionRequest selection : selections) {
            if (selection == null || !StringUtils.hasText(selection.title()) || !isVisualPartTitle(selection.title())) {
                continue;
            }
            String color = productColor(selection.title());
            if (!StringUtils.hasText(color)) {
                score += 8;
            } else if (targetColor.equals(color)) {
                score += 35;
            } else {
                score -= settings.colorMatchMode() == AiColorMatchMode.STRICT ? 120 : 38;
            }
        }
        return score;
    }

    private String dominantColor(List<BuildPartSelectionRequest> selections) {
        int white = 0;
        int black = 0;
        for (BuildPartSelectionRequest selection : selections) {
            if (selection == null || !StringUtils.hasText(selection.title()) || !isVisualPartTitle(selection.title())) {
                continue;
            }
            String color = productColor(selection.title());
            if ("WHITE".equals(color)) {
                white++;
            } else if ("BLACK".equals(color)) {
                black++;
            }
        }
        if (white >= 2 && white > black) {
            return "WHITE";
        }
        if (black >= 2 && black > white) {
            return "BLACK";
        }
        return "";
    }

    private boolean isVisualPartTitle(String title) {
        String text = title.toUpperCase(Locale.ROOT);
        return title.contains("显卡")
                || title.contains("主板")
                || title.contains("内存")
                || title.contains("机箱")
                || title.contains("散热")
                || text.contains("RTX")
                || text.contains("RX ");
    }

    private String productColor(String title) {
        String text = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (containsAny(text, "白色", "纯白", "雪", "white", "吹雪")) {
            return "WHITE";
        }
        if (containsAny(text, "黑色", "黑", "black", "暗黑")) {
            return "BLACK";
        }
        return "";
    }

    private String hardwarePartText(HardwarePart part) {
        if (part == null) {
            return "";
        }
        return (part.getModel()
                + " " + part.getSpecs()
                + " " + part.getFormFactor()
                + " " + part.getSupportedFormFactors()
                + " " + part.getMemoryType()).toUpperCase(Locale.ROOT);
    }

    private boolean hasAvailableSelection(BuildPartSelectionRequest selection) {
        return selection != null
                && StringUtils.hasText(selection.title())
                && selection.price() != null
                && selection.price() > 0
                && StringUtils.hasText(selection.link())
                && StringUtils.hasText(selection.skuId());
    }

    private String hardwareTypeLabel(HardwarePartType type) {
        return switch (type) {
            case CPU -> "CPU";
            case MOTHERBOARD -> "主板";
            case GPU -> "显卡";
            case MEMORY -> "内存";
            case STORAGE -> "硬盘";
            case PSU -> "电源";
            case CASE -> "机箱";
            case COOLER -> "散热器";
        };
    }

    private List<HardwarePart> required(Map<HardwarePartType, List<HardwarePart>> parts, HardwarePartType type) {
        List<HardwarePart> result = parts.getOrDefault(type, List.of());
        if (result.isEmpty()) {
            throw new BusinessException("硬件库缺少 " + type.name() + " 数据");
        }
        return result;
    }

    private List<HardwarePart> includePreferredCandidates(
            List<HardwarePart> base,
            List<HardwarePart> allParts,
            BuildPreferences preferences,
            HardwarePartType type,
            int limit
    ) {
        LinkedHashMap<Long, HardwarePart> selected = new LinkedHashMap<>();
        allParts.stream()
                .filter(part -> preferredPartMatch(part, preferences, type))
                .sorted(Comparator.comparingInt(HardwarePart::getPerformanceScore).reversed()
                        .thenComparing(HardwarePart::getModel))
                .forEach(part -> selected.put(part.getId(), part));
        base.forEach(part -> selected.put(part.getId(), part));
        return selected.values().stream().limit(limit).toList();
    }

    private boolean preferredPartMatch(HardwarePart part, BuildPreferences preferences, HardwarePartType type) {
        String model = part.getModel().toUpperCase(Locale.ROOT);
        if (type == HardwarePartType.CPU && preferences.preferAmdCpu()) {
            return model.contains("RYZEN");
        }
        if (type == HardwarePartType.CPU && preferences.preferIntelCpu()) {
            return model.contains("CORE");
        }
        if (type == HardwarePartType.CPU && preferences.preferX3dCpu()) {
            return model.contains("X3D");
        }
        if (type == HardwarePartType.GPU && StringUtils.hasText(preferences.preferredGpuModel())) {
            return model.contains(preferences.preferredGpuModel().toUpperCase(Locale.ROOT).replaceAll("\\s+", " "));
        }
        if (type == HardwarePartType.GPU && preferences.preferNvidiaGpu()) {
            return model.startsWith("RTX") || model.startsWith("GTX");
        }
        if (type == HardwarePartType.GPU && preferences.preferAmdGpu()) {
            return model.startsWith("RX");
        }
        if (type == HardwarePartType.MEMORY && preferences.preferredMemoryCapacityGb() > 0) {
            return model.contains(preferences.preferredMemoryCapacityGb() + "GB")
                    || model.contains(preferences.preferredMemoryCapacityGb() + "G");
        }
        if ((type == HardwarePartType.MOTHERBOARD || type == HardwarePartType.CASE)
                && StringUtils.hasText(preferences.preferredCaseSize())) {
            return casePreferenceScore(part, preferences) > 0;
        }
        if (type == HardwarePartType.COOLER && StringUtils.hasText(preferences.preferredCoolerType())) {
            return coolerPreferenceScore(part, preferences) > 0;
        }
        return false;
    }

    private List<HardwarePart> prioritizeCandidates(List<HardwarePart> parts, int limit) {
        List<HardwarePart> ranked = parts.stream()
                .sorted(Comparator.comparingInt(this::hardwareGenerationRank).reversed()
                        .thenComparing(Comparator.comparingInt(HardwarePart::getPerformanceScore).reversed())
                        .thenComparing(HardwarePart::getModel))
                .toList();
        if (ranked.size() <= limit) {
            return ranked;
        }
        LinkedHashMap<Long, HardwarePart> selected = new LinkedHashMap<>();
        int headCount = Math.max(1, limit - 2);
        ranked.stream().limit(headCount).forEach(part -> selected.put(part.getId(), part));
        parts.stream()
                .sorted(Comparator.comparingInt(HardwarePart::getPerformanceScore)
                        .thenComparing(HardwarePart::getModel))
                .limit(limit - selected.size())
                .forEach(part -> selected.put(part.getId(), part));
        return selected.values().stream().limit(limit).toList();
    }

    private int hardwareGenerationRank(HardwarePart part) {
        String model = part.getModel().toUpperCase();
        return switch (part.getType()) {
            case CPU -> cpuGenerationRank(model);
            case GPU -> gpuGenerationRank(model);
            case MOTHERBOARD -> motherboardGenerationRank(model);
            case MEMORY -> model.contains("DDR5") ? 2000 : model.contains("DDR4") ? 1000 : 0;
            case STORAGE -> model.contains("PCIE 5.0") ? 2000 : model.contains("PCIE 4.0") ? 1500 : 0;
            case PSU -> model.contains("ATX 3.1") ? 2000 : model.contains("ATX 3.0") ? 1800 : 0;
            case CASE, COOLER -> 0;
        };
    }

    private int cpuGenerationRank(String model) {
        if (model.contains("CORE ULTRA")) {
            return model.contains("PLUS") ? 2600 : 2500;
        }
        if (model.contains("9950X3D2")) {
            return 2700;
        }
        if (model.contains("9850X3D")) {
            return 2600;
        }
        if (model.contains("9950X3D") || model.contains("9900X3D") || model.contains("9800X3D")) {
            return 2500;
        }
        if (model.contains("RYZEN 9 99") || model.contains("RYZEN 7 97") || model.contains("RYZEN 5 96")) {
            return 2300;
        }
        if (model.contains("7800X3D")) {
            return 1800;
        }
        if (model.contains("14")) {
            return 1700;
        }
        if (model.contains("13")) {
            return 1600;
        }
        if (model.contains("12")) {
            return 1500;
        }
        return 0;
    }

    private int gpuGenerationRank(String model) {
        if (model.contains("RTX 50")) {
            return 2600;
        }
        if (model.contains("RX 90")) {
            return 2500;
        }
        if (model.contains("ARC B")) {
            return 2300;
        }
        if (model.contains("RTX 40") || model.contains("RX 7")) {
            return 1800;
        }
        return 0;
    }

    private int motherboardGenerationRank(String model) {
        if (model.contains("Z890") || model.contains("X870") || model.contains("X670")) {
            return 2600;
        }
        if (model.contains("B860") || model.contains("B850") || model.contains("Z790")) {
            return 2400;
        }
        if (model.contains("B650") || model.contains("B760")) {
            return 1800;
        }
        if (model.contains("B550")) {
            return 1200;
        }
        if (model.contains("H810")) {
            return 900;
        }
        return 0;
    }

    private boolean same(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }

    private boolean isBetterPlan(
            BuildPlan plan,
            BuildPlan current,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        if (current == null) {
            return true;
        }
        int planScore = planQualityScore(plan, budget, preferences, settings);
        int currentScore = planQualityScore(current, budget, preferences, settings);
        if (planScore != currentScore) {
            return planScore > currentScore;
        }
        return Math.abs(plan.totalPrice() - budget) < Math.abs(current.totalPrice() - budget);
    }

    private boolean isBetterFallbackPlan(
            BuildPlan plan,
            BuildPlan current,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        if (current == null) {
            return true;
        }
        boolean planWithin = plan.totalPrice() <= budget;
        boolean currentWithin = current.totalPrice() <= budget;
        if (planWithin || currentWithin) {
            return isBetterPlan(plan, current, budget, preferences, settings);
        }
        if (plan.totalPrice() != current.totalPrice()) {
            return plan.totalPrice() < current.totalPrice();
        }
        return planQualityScore(plan, budget, preferences, settings) > planQualityScore(current, budget, preferences, settings);
    }

    private int planQualityScore(
            BuildPlan plan,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        int score = plan.performanceScore() * 120;
        score += explicitPreferenceScore(plan.parts(), preferences) * 800;
        score += colorHarmonyScore(plan.selections(), preferences, settings) * 6;
        int priceGap = plan.totalPrice() - budget;
        if (priceGap <= 0) {
            score += 1800 - Math.abs(priceGap) / 8;
        } else {
            score -= priceGap / 3;
        }
        score -= budgetUnderusePenalty(plan.totalPrice(), budget, preferences) * 4;
        return score;
    }

    private boolean isBetterBudgetTargetPlan(
            BuildPlan plan,
            BuildPlan current,
            int budget,
            BuildPreferences preferences,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        if (current == null) {
            return true;
        }
        boolean planWithin = plan.totalPrice() <= budget;
        boolean currentWithin = current.totalPrice() <= budget;
        if (planWithin != currentWithin) {
            return planWithin;
        }
        return isBetterPlan(plan, current, budget, preferences, settings);
    }

    private int explicitPreferenceScore(List<HardwarePart> parts, BuildPreferences preferences) {
        int score = 0;
        for (HardwarePart part : parts) {
            String model = part.getModel().toUpperCase(Locale.ROOT);
            score += gameOptimizedCpuScore(part, preferences);
            if (part.getType() == HardwarePartType.CPU && preferences.preferAmdCpu() && model.contains("RYZEN")) {
                score += 5;
            }
            if (part.getType() == HardwarePartType.CPU && preferences.preferIntelCpu() && model.contains("CORE")) {
                score += 5;
            }
            if (part.getType() == HardwarePartType.CPU && preferences.preferX3dCpu() && model.contains("X3D")) {
                score += 6;
            }
            if (part.getType() == HardwarePartType.GPU && preferences.preferNvidiaGpu() && (model.startsWith("RTX") || model.startsWith("GTX"))) {
                score += 5;
            }
            if (part.getType() == HardwarePartType.GPU && preferences.preferAmdGpu() && model.startsWith("RX")) {
                score += 5;
            }
            if (part.getType() == HardwarePartType.GPU && preferences.defaultNvidiaGpuBias()) {
                if (model.startsWith("RTX") || model.startsWith("GTX")) {
                    score += 3;
                } else if (model.startsWith("RX")) {
                    score -= 1;
                }
            }
            if (part.getType() == HardwarePartType.GPU
                    && StringUtils.hasText(preferences.preferredGpuModel())
                    && model.contains(preferences.preferredGpuModel().toUpperCase(Locale.ROOT))) {
                score += 8;
            }
            if (part.getType() == HardwarePartType.MEMORY
                    && preferences.preferredMemoryCapacityGb() > 0
                    && (model.contains(preferences.preferredMemoryCapacityGb() + "GB")
                    || model.contains(preferences.preferredMemoryCapacityGb() + "G"))) {
                score += 5;
            }
            if ((part.getType() == HardwarePartType.CASE || part.getType() == HardwarePartType.MOTHERBOARD)
                    && casePreferenceScore(part, preferences) > 0) {
                score += 4;
            }
            if (part.getType() == HardwarePartType.COOLER && coolerPreferenceScore(part, preferences) > 0) {
                score += 4;
            }
        }
        return score;
    }

    private int gameOptimizedCpuScore(HardwarePart part, BuildPreferences preferences) {
        if (part.getType() != HardwarePartType.CPU
                || !preferences.gamingUse()
                || preferences.productivityUse()
                || preferences.preferIntelCpu()) {
            return 0;
        }
        String model = part.getModel().toUpperCase(Locale.ROOT);
        if (model.contains("9950X3D2")) {
            return 8;
        }
        if (model.contains("9850X3D")) {
            return 7;
        }
        if (model.contains("9950X3D") || model.contains("9900X3D")) {
            return 6;
        }
        if (model.contains("9800X3D")) {
            return 5;
        }
        if (model.contains("7800X3D") || model.contains("5700X3D")) {
            return 2;
        }
        if (model.contains("9700X")) {
            return 2;
        }
        if (model.contains("9600X")) {
            return 1;
        }
        if (model.contains("9950X") || model.contains("9900X")) {
            return -4;
        }
        if (model.contains("CORE ULTRA 9")) {
            return -2;
        }
        return 0;
    }

    private Map<Long, BuildPartSelectionRequest> createProductSelectionMap(Map<HardwarePartType, List<HardwarePart>> typedParts, BuildPreferences preferences) {
        return createProductSelectionMap(typedParts, preferences, null);
    }

    private Map<Long, BuildPartSelectionRequest> createProductSelectionMap(Map<HardwarePartType, List<HardwarePart>> typedParts, BuildPreferences preferences, BuildProgressReporter progressReporter) {
        Map<Long, BuildPartSelectionRequest> result = new LinkedHashMap<>();
        int total = typedParts.values().stream().mapToInt(List::size).sum();
        int current = 0;
        for (List<HardwarePart> list : typedParts.values()) {
            for (HardwarePart part : list) {
                current++;
                if (progressReporter != null) {
                    int progress = 28 + Math.min(54, Math.round(current * 54f / Math.max(1, total)));
                    progressReporter.update(progress, "检索京东商品", hardwareTypeLabel(part.getType()) + " " + part.getModel());
                }
                jdProductSearchService.bestAvailableForPart(
                        part,
                        preferences.preferredColor(),
                        false,
                        preferences.preferredCaseStyle(),
                        preferences.preferredCoolerType()
                ).ifPresent(product -> result.put(part.getId(), new BuildPartSelectionRequest(
                            part.getId(),
                            product.title(),
                            product.price(),
                            product.image(),
                            product.link(),
                            product.skuId(),
                            StringUtils.hasText(product.matchedModel()) ? product.matchedModel() : part.getModel()
                    )));
            }
        }
        return result;
    }

    private BuildPartSelectionRequest selectionForPart(HardwarePart part, Map<Long, BuildPartSelectionRequest> selections) {
        BuildPartSelectionRequest selection = selections.get(part.getId());
        if (selection != null) {
            return selection;
        }
        return new BuildPartSelectionRequest(part.getId(), "", 0, "", "", "", part.getModel());
    }

    private int totalPrice(List<HardwarePart> parts, List<BuildPartSelectionRequest> selections) {
        Map<Long, Integer> productPrices = selections.stream()
                .filter(item -> item != null && item.hardwarePartId() != null)
                .collect(Collectors.toMap(
                        BuildPartSelectionRequest::hardwarePartId,
                        item -> item.price() == null ? 0 : item.price(),
                        (left, right) -> left
                ));
        int total = 0;
        for (HardwarePart part : parts) {
            int productPrice = productPrices.getOrDefault(part.getId(), 0);
            total += productPrice > 0 ? productPrice : referencePrice(part);
        }
        return total;
    }

    private int referencePrice(HardwarePart part) {
        return estimatedPartPrice(part);
    }

    private int estimatedPartPrice(HardwarePart part) {
        String model = part.getModel().toUpperCase(Locale.ROOT);
        return switch (part.getType()) {
            case CPU -> estimatedCpuPrice(model);
            case GPU -> estimatedGpuPrice(model);
            case MOTHERBOARD -> estimatedMotherboardPrice(model);
            case MEMORY -> estimatedMemoryPrice(model);
            case STORAGE -> estimatedStoragePrice(model);
            case PSU -> estimatedPsuPrice(model);
            case CASE -> estimatedCasePrice(model);
            case COOLER -> estimatedCoolerPrice(model);
        };
    }

    private int estimatedCpuPrice(String model) {
        if (model.contains("9950X3D2")) return 6500;
        if (model.contains("9950X3D")) return 5200;
        if (model.contains("9950X")) return 4300;
        if (model.contains("9900X3D")) return 3800;
        if (model.contains("9900X")) return 3200;
        if (model.contains("9850X3D")) return 3600;
        if (model.contains("9800X3D")) return 3400;
        if (model.contains("9700X")) return 1900;
        if (model.contains("9600X")) return 1400;
        if (model.contains("7800X3D")) return 2500;
        if (model.contains("7500F")) return 900;
        if (model.contains("5700X3D")) return 1000;
        if (model.contains("5600")) return 600;
        if (model.contains("CORE ULTRA 9")) return 4200;
        if (model.contains("CORE ULTRA 7")) return 2800;
        if (model.contains("CORE ULTRA 5")) return 1800;
        if (model.contains("14700")) return 2400;
        if (model.contains("14600")) return 1500;
        if (model.contains("13490")) return 1100;
        if (model.contains("12400")) return 750;
        return 1500;
    }

    private int estimatedGpuPrice(String model) {
        if (model.contains("RTX 5090")) return 22000;
        if (model.contains("RTX 5080")) return 9000;
        if (model.contains("RTX 5070 TI")) return 6200;
        if (model.contains("RTX 5070")) return 4800;
        if (model.contains("RTX 5060 TI")) return 3000;
        if (model.contains("RTX 5060")) return 2300;
        if (model.contains("RTX 5050")) return 1600;
        if (model.contains("RTX 4070 SUPER")) return 4300;
        if (model.contains("RTX 4070")) return 3400;
        if (model.contains("RTX 4060 TI")) return 2600;
        if (model.contains("RTX 4060")) return 2100;
        if (model.contains("RX 9070 XT")) return 5200;
        if (model.contains("RX 9070")) return 4300;
        if (model.contains("RX 9060 XT")) return 2600;
        if (model.contains("RX 7800 XT")) return 3500;
        if (model.contains("RX 7700 XT")) return 2900;
        return 2500;
    }

    private int estimatedMotherboardPrice(String model) {
        if (model.contains("X870E")) return 2500;
        if (model.contains("X870")) return 1800;
        if (model.contains("B850M")) return 900;
        if (model.contains("B850")) return 1100;
        if (model.contains("B650E")) return 1200;
        if (model.contains("B650M")) return 700;
        if (model.contains("B650")) return 900;
        if (model.contains("Z890")) return 2000;
        if (model.contains("B860M")) return 800;
        if (model.contains("B860")) return 950;
        if (model.contains("H810")) return 550;
        if (model.contains("Z790")) return 1600;
        if (model.contains("B760M")) return 700;
        if (model.contains("B760")) return 850;
        if (model.contains("B660M")) return 550;
        if (model.contains("B550M")) return 550;
        return 800;
    }

    private int estimatedMemoryPrice(String model) {
        if (model.contains("64GB") && model.contains("DDR5")) return 4200;
        if (model.contains("64GB")) return 3200;
        if (model.contains("32GB") && model.contains("DDR5") && model.contains("7200")) return 2600;
        if (model.contains("32GB") && model.contains("DDR5") && model.contains("6400")) return 2300;
        if (model.contains("32GB") && model.contains("DDR5")) return 2100;
        if (model.contains("16GB") && model.contains("DDR5")) return 1100;
        if (model.contains("32GB") && model.contains("DDR4")) return 1200;
        if (model.contains("16GB") && model.contains("DDR4")) return 700;
        return 1500;
    }

    private int estimatedStoragePrice(String model) {
        if (model.contains("PCIe 5.0".toUpperCase(Locale.ROOT)) && model.contains("2TB")) return 2200;
        if (model.contains("PCIe 5.0".toUpperCase(Locale.ROOT)) && model.contains("1TB")) return 1050;
        if (model.contains("4TB")) return 3000;
        if (model.contains("2TB")) return 1350;
        if (model.contains("1TB")) return 700;
        return 900;
    }

    private int estimatedPsuPrice(String model) {
        if (model.contains("1000W")) return 900;
        if (model.contains("850W") && model.contains("ATX 3.1")) return 650;
        if (model.contains("750W") && model.contains("ATX 3.1")) return 520;
        if (model.contains("650W") && model.contains("ATX 3.1")) return 420;
        if (model.contains("850W")) return 580;
        if (model.contains("750W")) return 450;
        if (model.contains("650W") && model.contains("BRONZE")) return 280;
        if (model.contains("650W")) return 360;
        return 400;
    }

    private int estimatedCasePrice(String model) {
        if (model.contains("E-ATX")) return 700;
        if (model.contains("ITX")) return 450;
        if (model.contains("M-ATX")) return 300;
        return 350;
    }

    private int estimatedCoolerPrice(String model) {
        if (model.contains("360")) return 520;
        if (model.contains("280")) return 430;
        if (model.contains("240")) return 300;
        if (model.contains("DUAL TOWER")) return 190;
        if (model.contains("LOW PROFILE")) return 160;
        return 100;
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
                .append(StringUtils.hasText(build.serialNumber()) ? "（编号 " + build.serialNumber() + "）" : "")
                .append("，总价 ¥").append(build.totalPrice())
                .append("，性能 ").append(build.performanceScore()).append(" 分。\n");
        for (HardwarePartResponse part : build.parts()) {
            String title = StringUtils.hasText(part.productTitle()) ? part.productTitle() : part.model();
            builder.append(part.type()).append("：")
                    .append(title);
            if (!title.equals(part.model())) {
                builder.append("（标准型号：").append(part.model()).append("）");
            }
            if (part.productPrice() != null && part.productPrice() > 0) {
                builder.append("，¥").append(part.productPrice());
            } else {
                builder.append("，暂无京东价格");
            }
            if (!hasPerformanceScore(part.type())) {
                builder.append("，不参与性能评分");
            } else {
                builder.append("，性能 ")
                        .append(part.performanceScore()).append(" 分");
            }
            if (StringUtils.hasText(part.productLink())) {
                builder.append("，京东链接：").append(part.productLink());
            }
            if (StringUtils.hasText(part.skuId())) {
                builder.append("，京东 SKU：").append(part.skuId());
            }
            builder.append("\n");
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
        return saveAssistantAndReturn(
                conversation,
                content,
                fromCache,
                directReply,
                intent,
                subIntent,
                safety,
                generatedBuild,
                List.of()
        );
    }

    private ChatResponse saveAssistantAndReturn(
            ChatConversation conversation,
            String content,
            boolean fromCache,
            boolean directReply,
            String intent,
            String subIntent,
            SafetyResult safety,
            BuildConfigResponse generatedBuild,
            List<ChatNewsReferenceResponse> newsReferences
    ) {
        List<ChatNewsReferenceResponse> references = newsReferences == null ? List.of() : newsReferences;
        messageRepository.save(new ChatMessage(
                conversation.getId(),
                "assistant",
                content,
                generatedBuild == null ? null : generatedBuild.id(),
                generatedBuild == null ? "" : generatedBuild.title(),
                serializeNewsReferences(references)
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
                generatedBuild == null ? "" : generatedBuild.serialNumber(),
                generatedBuild,
                references,
                null
        );
    }

    private String serializeNewsReferences(List<ChatNewsReferenceResponse> newsReferences) {
        if (newsReferences == null || newsReferences.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(newsReferences);
        } catch (Exception exception) {
            return "";
        }
    }

    private List<ChatNewsReferenceResponse> parseNewsReferences(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<ChatNewsReferenceResponse>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String callModel(
            String message,
            String memoryContext,
            String conversationContext,
            String buildContext,
            String newsContext,
            IntentAnalysis intent,
            SafetyResult safety,
            AiSettingsService.EffectiveAiSettings settings
    ) {
        if (!StringUtils.hasText(settings.apiKey())) {
            return "AI 服务尚未配置密钥，请稍后再试。";
        }
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(settings.apiKey())
                .baseUrl(settings.baseUrl())
                .modelName(settings.modelName())
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();
        String extraPrompt = StringUtils.hasText(settings.extraSystemPrompt())
                ? "\n管理员附加要求：\n" + settings.extraSystemPrompt().trim() + "\n"
                : "";
        String prompt = """
                你是智能 DIY 电脑助手。请用简体中文回答，重点关注预算、用途、兼容性、散热、电源和升级空间。
                回复要求：
                1. 不要使用 Markdown 语法，不要使用星号、井号、表格或代码块。
                2. 控制在 %d 到 %d 个汉字之间，信息要完整但不要啰嗦。
                3. 优先给出结论，再给 3 到 5 条关键理由或建议。
                4. 如果信息不足，最后用一句话追问最关键的缺失条件。
                5. 如果安全等级是 WARN 或 URGENT，必须明确提醒用户先确认关键风险，不能直接给出“放心买”的结论。
                6. 需要结合当前会话上下文回答，但不要提及“根据上下文”这类字样。
                7. 如果系统提供了已生成配置单，直接说明已保存到配置页，并围绕该配置做简洁解释。
                8. 如果用户附加了配置单，优先围绕附加配置单评价、优化或回答问题。
                9. 当用户要求生成、保存、配一套、出一台或给出具体预算用途时，不要只写文字配置；应依赖系统自动生成的配置单上下文。若没有配置单上下文，必须说明无法保存的原因。
                10. 配置单上下文里的京东商品名、京东 SKU、链接和价格来自实时检索或数据库快照，优先级高于你的训练知识；只要上下文出现京东链接或 SKU，不要说该商品“尚未上市”“未发布”“买不到”。
                11. 不要用旧知识否定 RX 9060 XT、RX 9070、RTX 50 系等上下文商品；若需提醒，只能说以当前京东页面库存和价格为准。
                12. 用户没有主动提颜色时，不要评价黑色、白色、海景房、外观配色等内容。
                13. 用户询问游戏帧率时，需要基于配置单 CPU/GPU 定位给出保守帧率区间；如果预算达不到目标帧率，直接说明需要加预算或降低画质、分辨率、帧率目标。
                14. 长期用户画像记忆只代表用户过去反复或明确表达过的默认偏好，当前用户需求优先级更高；如果当前输入与长期记忆冲突，必须以当前输入为准。
                15. 不要把长期记忆扩写成用户没有明确提出的硬件参数、实时价格、商品链接或 SKU。
                16. 如果系统提供了热点新闻上下文，必须只基于这些新闻总结事实和影响，并在回答中说明下方附有热点来源；不要编造未提供的新闻或链接。
                17. 内存和硬盘价格评价必须以配置单或京东快照为准；不要用旧知识里的低价锚点判定“远超市场”。如果内存或固态显得偏贵，表达为近期行情偏高、购买前建议按同规格再比价。
                18. 如果工具路由是 ASK_CLARIFICATION，只追问生成配置单所缺的最关键条件，不要自行列出完整配置。
                19. 如果工具路由是 SUMMARIZE_NEWS 但热点新闻上下文为“无”，直接说明目前没有可引用热点来源，不要编造新闻。
                %s

                意图识别：
                大类：%s
                子意图：%s
                识别依据：%s
                提取参数：%s
                第二层建议进入安全检查：%s
                第二层风险等级：%s
                工具路由：%s
                工具置信度：%.2f
                工具路由原因：%s
                工具参数：%s

                安全规则：
                等级：%s
                提醒：%s

                长期用户画像记忆：
                %s

                当前会话上下文：
                %s

                配置单上下文：
                %s

                热点新闻上下文：
                %s

                用户需求：
                %s
                """.formatted(
                settings.replyMinChars(),
                settings.replyMaxChars(),
                extraPrompt,
                intent.intent(),
                intent.subIntent(),
                intent.reasoning(),
                intent.extractedDataJson(),
                intent.safetyCheckRequired() ? "是" : "否",
                intent.safetyRiskLevel(),
                intent.toolAction().name(),
                intent.toolConfidence(),
                intent.toolReason(),
                intent.toolArgsJson(),
                safety.level().name(),
                safety.message().isBlank() ? "无" : safety.message(),
                StringUtils.hasText(memoryContext) ? memoryContext : "无",
                conversationContext,
                buildContext,
                StringUtils.hasText(newsContext) ? newsContext : "无",
                message
        );
        try {
            String reply = model.generate(prompt);
            if (!StringUtils.hasText(reply)) {
                return "AI 服务这次没有生成有效回复，请稍后再试。";
            }
            return reply.trim();
        } catch (RuntimeException exception) {
            return "AI 服务暂时响应失败，请稍后再试。";
        }
    }

    private record BuildGenerationResult(BuildConfigResponse build, String failureMessage) {

        private static BuildGenerationResult none() {
            return new BuildGenerationResult(null, "");
        }

        private static BuildGenerationResult generated(BuildConfigResponse build) {
            return new BuildGenerationResult(build, "");
        }

        private static BuildGenerationResult failed(String failureMessage) {
            return new BuildGenerationResult(null, failureMessage);
        }

        private boolean failed() {
            return StringUtils.hasText(failureMessage);
        }
    }

    private record NewsContext(String promptContext, List<ChatNewsReferenceResponse> references) {

        private static NewsContext empty() {
            return new NewsContext("无", List.of());
        }
    }

    private record NewsMatch(News news, int score) {
    }

    private record BuildPlan(List<HardwarePart> parts, List<BuildPartSelectionRequest> selections, int totalPrice, int performanceScore) {
    }

    private record BuildTaskSnapshot(Long id, Long userId, Long conversationId, Long attachedBuildId, String message) {
    }

    @FunctionalInterface
    private interface BuildProgressReporter {
        void update(int progress, String title, String description);
    }

    private record BuildPreferences(
            boolean preferNvidiaGpu,
            boolean preferAmdGpu,
            boolean preferAmdCpu,
            boolean preferIntelCpu,
            String preferredColor,
            String preferredGpuModel,
            String preferredMemoryType,
            int preferredMemoryCapacityGb,
            boolean preferX3dCpu,
            String preferredCaseSize,
            String preferredCaseStyle,
            String preferredCoolerType,
            boolean useBudgetFloor,
            boolean gamingUse,
            boolean productivityUse,
            boolean explicitColorPreference,
            boolean defaultNvidiaGpuBias
    ) {
    }

    private enum CpuTier {
        ENTRY,
        MAINSTREAM,
        PERFORMANCE,
        FLAGSHIP
    }

    private enum BoardTier {
        ENTRY,
        MAINSTREAM,
        PERFORMANCE,
        ENTHUSIAST
    }

    private boolean hasPerformanceScore(HardwarePartType type) {
        return type == HardwarePartType.CPU
                || type == HardwarePartType.GPU
                || type == HardwarePartType.MEMORY
                || type == HardwarePartType.STORAGE;
    }
}
