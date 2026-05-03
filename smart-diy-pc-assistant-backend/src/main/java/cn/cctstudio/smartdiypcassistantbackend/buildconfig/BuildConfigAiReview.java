package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "pc_build_config_ai_review",
        uniqueConstraints = @UniqueConstraint(name = "uk_build_ai_review_build_user", columnNames = {"build_config_id", "user_id"}),
        indexes = {
                @Index(name = "idx_build_ai_review_user", columnList = "user_id, updated_at"),
                @Index(name = "idx_build_ai_review_build", columnList = "build_config_id")
        }
)
public class BuildConfigAiReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "build_config_id", nullable = false)
    private Long buildConfigId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "review_content", nullable = false, columnDefinition = "LONGTEXT")
    private String reviewContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BuildConfigAiReview() {
    }

    public BuildConfigAiReview(Long buildConfigId, Long userId, String reviewContent) {
        this.buildConfigId = buildConfigId;
        this.userId = userId;
        this.reviewContent = reviewContent == null ? "" : reviewContent;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void updateReview(String reviewContent) {
        this.reviewContent = reviewContent == null ? "" : reviewContent;
        this.updatedAt = LocalDateTime.now();
    }

    public String getReviewContent() {
        return reviewContent == null ? "" : reviewContent;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
