package cn.cctstudio.smartdiypcassistantbackend.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_comment")
public class CommunityComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String imageUrls;

    private Long buildConfigId;

    @Column(nullable = false)
    private boolean visible;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected CommunityComment() {
    }

    public CommunityComment(Long postId, Long userId, String content, String imageUrls, Long buildConfigId) {
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.imageUrls = imageUrls;
        this.buildConfigId = buildConfigId;
        this.visible = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public String getImageUrls() {
        return imageUrls == null ? "" : imageUrls;
    }

    public Long getBuildConfigId() {
        return buildConfigId;
    }

    public boolean isVisible() {
        return visible;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
