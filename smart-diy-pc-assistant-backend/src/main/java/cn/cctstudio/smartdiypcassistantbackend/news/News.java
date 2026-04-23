package cn.cctstudio.smartdiypcassistantbackend.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_hotspot")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(length = 30)
    private String tag;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 500)
    private String coverUrl;

    @Column(nullable = false)
    private Long viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected News() {
    }

    public News(String title, String summary, String tag, String content, String coverUrl, NewsStatus status) {
        this.title = title;
        this.summary = summary;
        this.tag = tag;
        this.content = content;
        this.coverUrl = coverUrl;
        this.status = status;
        this.viewCount = 0L;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String title, String summary, String tag, String content, String coverUrl, NewsStatus status) {
        this.title = title;
        this.summary = summary;
        this.tag = tag;
        this.content = content;
        this.coverUrl = coverUrl;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getTag() {
        return tag == null || tag.isBlank() ? "科技新闻" : tag;
    }

    public String getContent() {
        return content;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getCoverKey() {
        return coverUrl;
    }

    public long getViewCount() {
        return viewCount == null ? 0 : viewCount;
    }

    public void increaseViewCount() {
        this.viewCount = getViewCount() + 1;
    }

    public NewsStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
