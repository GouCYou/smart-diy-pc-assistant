package cn.cctstudio.smartdiypcassistantbackend.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_feedback",
        indexes = {
                @Index(name = "idx_user_feedback_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_user_feedback_status", columnList = "status, created_at")
        }
)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Lob
    @Column(name = "image_keys", nullable = false, columnDefinition = "TEXT")
    private String imageKeys;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected Feedback() {
    }

    public Feedback(Long userId, String content, String imageKeys) {
        this.userId = userId;
        this.content = content;
        this.imageKeys = imageKeys == null ? "[]" : imageKeys;
        this.status = FeedbackStatus.SUBMITTED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void markReceived() {
        if (status == FeedbackStatus.RESOLVED) {
            return;
        }
        status = FeedbackStatus.RECEIVED;
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    public void markResolved() {
        status = FeedbackStatus.RESOLVED;
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
        resolvedAt = LocalDateTime.now();
        updatedAt = resolvedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public String getImageKeys() {
        return imageKeys == null ? "[]" : imageKeys;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}
