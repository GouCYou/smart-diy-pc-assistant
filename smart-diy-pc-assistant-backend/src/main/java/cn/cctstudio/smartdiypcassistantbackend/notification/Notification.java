package cn.cctstudio.smartdiypcassistantbackend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recipientId;

    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Long relatedPostId;

    private Long relatedCommentId;

    private Long relatedNewsId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(
            Long recipientId,
            Long actorId,
            NotificationType type,
            String title,
            String content,
            Long relatedPostId,
            Long relatedCommentId,
            Long relatedNewsId
    ) {
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.relatedPostId = relatedPostId;
        this.relatedCommentId = relatedCommentId;
        this.relatedNewsId = relatedNewsId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public Long getActorId() {
        return actorId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Long getRelatedPostId() {
        return relatedPostId;
    }

    public Long getRelatedCommentId() {
        return relatedCommentId;
    }

    public Long getRelatedNewsId() {
        return relatedNewsId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
