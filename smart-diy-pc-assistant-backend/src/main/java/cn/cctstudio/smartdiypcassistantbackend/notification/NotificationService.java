package cn.cctstudio.smartdiypcassistantbackend.notification;

import cn.cctstudio.smartdiypcassistantbackend.community.dto.AuthorResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.notification.dto.AdminSystemNotificationRequest;
import cn.cctstudio.smartdiypcassistantbackend.notification.dto.AdminSystemNotificationResponse;
import cn.cctstudio.smartdiypcassistantbackend.notification.dto.NotificationResponse;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MediaUrlService mediaUrlService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            MediaUrlService mediaUrlService
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.mediaUrlService = mediaUrlService;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long userId) {
        requireLogin(userId);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        requireLogin(userId);
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse detail(Long userId, Long id) {
        requireLogin(userId);
        Notification notification = notificationRepository.findByIdAndRecipientId(id, userId)
                .orElseThrow(() -> new BusinessException("通知不存在"));
        if (notification.getType() != NotificationType.SYSTEM) {
            throw new BusinessException("只有系统通知可以查看详情");
        }
        notification.markRead();
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        requireLogin(userId);
        notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
                .forEach(Notification::markRead);
    }

    @Transactional
    public AdminSystemNotificationResponse sendSystemNotification(AdminSystemNotificationRequest request) {
        String title = request.title().trim();
        String content = request.content().trim();
        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = userRepository.findAll().stream()
                .filter(User::isEnabled)
                .map(user -> new Notification(
                        user.getId(),
                        null,
                        NotificationType.SYSTEM,
                        title,
                        content,
                        null,
                        null,
                        null
                ))
                .toList();
        notificationRepository.saveAll(notifications);
        return new AdminSystemNotificationResponse(title, content, notifications.size(), now);
    }

    @Transactional
    public void notifyFollow(Long actorId, Long recipientId) {
        if (!shouldNotify(actorId, recipientId)) {
            return;
        }
        AuthorResponse actor = author(actorId);
        saveUserNotification(
                recipientId,
                actorId,
                NotificationType.FOLLOW,
                actor.nickname() + " 关注了你",
                "对方开始关注你的个人空间。",
                null,
                null,
                null
        );
    }

    @Transactional
    public void notifyCommunityPostReply(Long actorId, Long recipientId, Long postId, Long commentId, String content) {
        if (!shouldNotify(actorId, recipientId)) {
            return;
        }
        AuthorResponse actor = author(actorId);
        saveUserNotification(
                recipientId,
                actorId,
                NotificationType.COMMUNITY_POST_REPLY,
                actor.nickname() + " 回复了你的动态",
                replyContent(content),
                postId,
                commentId,
                null
        );
    }

    @Transactional
    public void notifyCommunityCommentReply(Long actorId, Long recipientId, Long postId, Long commentId, String content) {
        if (!shouldNotify(actorId, recipientId)) {
            return;
        }
        AuthorResponse actor = author(actorId);
        saveUserNotification(
                recipientId,
                actorId,
                NotificationType.COMMUNITY_COMMENT_REPLY,
                actor.nickname() + " 回复了你的评论",
                replyContent(content),
                postId,
                commentId,
                null
        );
    }

    @Transactional
    public void notifyNewsCommentReply(Long actorId, Long recipientId, Long newsId, Long commentId, String content) {
        if (!shouldNotify(actorId, recipientId)) {
            return;
        }
        AuthorResponse actor = author(actorId);
        saveUserNotification(
                recipientId,
                actorId,
                NotificationType.NEWS_COMMENT_REPLY,
                actor.nickname() + " 回复了你的评论",
                replyContent(content),
                null,
                commentId,
                newsId
        );
    }

    @Transactional
    public void notifyCommunityPostMention(Long actorId, Long recipientId, Long postId, String content) {
        if (!shouldNotify(actorId, recipientId)) {
            return;
        }
        AuthorResponse actor = author(actorId);
        saveUserNotification(
                recipientId,
                actorId,
                NotificationType.COMMUNITY_POST_MENTION,
                actor.nickname() + " 在动态中提到了你",
                replyContent(content),
                postId,
                null,
                null
        );
    }

    @Transactional
    public void notifyCommunityCommentMention(Long actorId, Long recipientId, Long postId, Long commentId, String content) {
        if (!shouldNotify(actorId, recipientId)) {
            return;
        }
        AuthorResponse actor = author(actorId);
        saveUserNotification(
                recipientId,
                actorId,
                NotificationType.COMMUNITY_COMMENT_MENTION,
                actor.nickname() + " 在评论中提到了你",
                replyContent(content),
                postId,
                commentId,
                null
        );
    }

    @Transactional
    public void notifyNewsCommentMention(Long actorId, Long recipientId, Long newsId, Long commentId, String content) {
        if (!shouldNotify(actorId, recipientId)) {
            return;
        }
        AuthorResponse actor = author(actorId);
        saveUserNotification(
                recipientId,
                actorId,
                NotificationType.NEWS_COMMENT_MENTION,
                actor.nickname() + " 在新闻评论中提到了你",
                replyContent(content),
                null,
                commentId,
                newsId
        );
    }

    @Transactional
    public void notifyFeedbackResolved(Long recipientId, Long feedbackId) {
        if (recipientId == null || !userRepository.existsById(recipientId)) {
            return;
        }
        saveUserNotification(
                recipientId,
                null,
                NotificationType.FEEDBACK_RESOLVED,
                "你的反馈已解决",
                "反馈 #" + feedbackId + " 已由管理员标记为已解决，感谢你帮我们改进产品。",
                null,
                null,
                null
        );
    }

    @Transactional
    public void notifyPriceAlert(Long recipientId, String title, String content) {
        if (recipientId == null || !userRepository.existsById(recipientId)) {
            return;
        }
        saveUserNotification(
                recipientId,
                null,
                NotificationType.PRICE_ALERT,
                title,
                content,
                null,
                null,
                null
        );
    }

    private void saveUserNotification(
            Long recipientId,
            Long actorId,
            NotificationType type,
            String title,
            String content,
            Long relatedPostId,
            Long relatedCommentId,
            Long relatedNewsId
    ) {
        notificationRepository.save(new Notification(
                recipientId,
                actorId,
                type,
                title,
                content,
                relatedPostId,
                relatedCommentId,
                relatedNewsId
        ));
    }

    private boolean shouldNotify(Long actorId, Long recipientId) {
        return actorId != null
                && recipientId != null
                && !Objects.equals(actorId, recipientId)
                && userRepository.existsById(recipientId);
    }

    private String replyContent(String content) {
        String value = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(value)) {
            return "回复了图片内容";
        }
        if (value.length() <= 180) {
            return value;
        }
        return value.substring(0, 180) + "...";
    }

    private void requireLogin(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getActorId() == null ? null : author(notification.getActorId()),
                notification.getRelatedPostId(),
                notification.getRelatedCommentId(),
                notification.getRelatedNewsId(),
                notification.isUnread(),
                notification.getCreatedAt()
        );
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
}
