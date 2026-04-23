package cn.cctstudio.smartdiypcassistantbackend.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_comment")
public class NewsComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long newsId;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String imageUrls;

    @Column(nullable = false)
    private boolean visible;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected NewsComment() {
    }

    public NewsComment(Long newsId, Long userId, String content, String imageUrls) {
        this.newsId = newsId;
        this.userId = userId;
        this.content = content;
        this.imageUrls = imageUrls;
        this.visible = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getNewsId() {
        return newsId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public String getImageUrls() {
        return imageUrls == null ? "" : imageUrls;
    }

    public boolean isVisible() {
        return visible;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
