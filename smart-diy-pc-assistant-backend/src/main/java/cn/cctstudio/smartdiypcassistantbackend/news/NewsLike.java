package cn.cctstudio.smartdiypcassistantbackend.news;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_like")
public class NewsLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long newsId;
    private Long userId;
    private LocalDateTime createdAt;

    protected NewsLike() {
    }

    public NewsLike(Long newsId, Long userId) {
        this.newsId = newsId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }
}
