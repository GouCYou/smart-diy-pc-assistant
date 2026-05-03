package cn.cctstudio.smartdiypcassistantbackend.feedback;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.feedback.dto.FeedbackRequest;
import cn.cctstudio.smartdiypcassistantbackend.feedback.dto.FeedbackResponse;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.notification.NotificationService;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final MediaUrlService mediaUrlService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            UserRepository userRepository,
            MediaUrlService mediaUrlService,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.mediaUrlService = mediaUrlService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FeedbackResponse create(Long userId, FeedbackRequest request) {
        requireLogin(userId);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new BusinessException("反馈内容不能为空");
        }
        List<String> imageKeys = cleanImageKeys(request.imageKeys());
        Feedback feedback = feedbackRepository.save(new Feedback(userId, content, serializeImageKeys(imageKeys)));
        return toResponse(feedback);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listMine(Long userId) {
        requireLogin(userId);
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> adminList() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FeedbackResponse markReceived(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException("反馈不存在"));
        feedback.markReceived();
        return toResponse(feedback);
    }

    @Transactional
    public FeedbackResponse markResolved(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException("反馈不存在"));
        boolean alreadyResolved = feedback.getStatus() == FeedbackStatus.RESOLVED;
        feedback.markResolved();
        if (!alreadyResolved) {
            notificationService.notifyFeedbackResolved(feedback.getUserId(), feedback.getId());
        }
        return toResponse(feedback);
    }

    private FeedbackResponse toResponse(Feedback feedback) {
        List<String> imageKeys = parseImageKeys(feedback.getImageKeys());
        return new FeedbackResponse(
                feedback.getId(),
                author(feedback.getUserId()),
                feedback.getContent(),
                imageKeys,
                imageKeys.stream().map(mediaUrlService::toPublicUrl).toList(),
                feedback.getStatus().name(),
                statusLabel(feedback.getStatus()),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt(),
                feedback.getReceivedAt(),
                feedback.getResolvedAt()
        );
    }

    private List<String> cleanImageKeys(List<String> imageKeys) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return List.of();
        }
        return imageKeys.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .limit(9)
                .toList();
    }

    private String serializeImageKeys(List<String> imageKeys) {
        try {
            return objectMapper.writeValueAsString(imageKeys);
        } catch (JsonProcessingException ignored) {
            return "[]";
        }
    }

    private List<String> parseImageKeys(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String statusLabel(FeedbackStatus status) {
        return switch (status) {
            case SUBMITTED -> "已提交";
            case RECEIVED -> "已收到";
            case RESOLVED -> "已解决";
        };
    }

    private AuthorResponse author(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new AuthorResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getNickname(),
                        mediaUrlService.toPublicUrl(user.getAvatarKey())
                ))
                .orElse(new AuthorResponse(userId, "用户", "用户", ""));
    }

    private void requireLogin(Long userId) {
        if (userId == null || userId <= 0 || !userRepository.existsById(userId)) {
            throw new BusinessException(401, "请先登录");
        }
    }
}
