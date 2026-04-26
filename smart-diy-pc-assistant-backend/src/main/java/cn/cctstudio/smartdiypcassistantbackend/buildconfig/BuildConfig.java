package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

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
@Table(name = "pc_build_config")
public class BuildConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 40)
    private String budget;

    @Column(length = 80)
    private String scenario;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String note;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String partIds;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String partSelections;

    private Integer totalPrice;

    private Integer performanceScore;

    @Column(length = 20)
    private String compatibilityStatus;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String compatibilityMessages;

    @Column(nullable = false, length = 240)
    private String summary;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BuildConfigSource source;

    @Column(nullable = false)
    private boolean favorite;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected BuildConfig() {
    }

    public BuildConfig(
            Long userId,
            String title,
            String note,
            String partIds,
            String partSelections,
            Integer totalPrice,
            Integer performanceScore,
            String compatibilityStatus,
            String compatibilityMessages,
            String summary,
            String content,
            BuildConfigSource source
    ) {
        this.userId = userId;
        this.title = title;
        this.budget = "";
        this.scenario = "";
        this.note = note;
        this.partIds = partIds;
        this.partSelections = partSelections;
        this.totalPrice = totalPrice;
        this.performanceScore = performanceScore;
        this.compatibilityStatus = compatibilityStatus;
        this.compatibilityMessages = compatibilityMessages;
        this.summary = summary;
        this.content = content;
        this.source = source;
        this.favorite = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void updateFavorite(boolean favorite) {
        this.favorite = favorite;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateConfig(
            String title,
            String note,
            String partIds,
            String partSelections,
            Integer totalPrice,
            Integer performanceScore,
            String compatibilityStatus,
            String compatibilityMessages,
            String summary,
            String content,
            BuildConfigSource source
    ) {
        this.title = title;
        this.note = note;
        this.partIds = partIds;
        this.partSelections = partSelections;
        this.totalPrice = totalPrice;
        this.performanceScore = performanceScore;
        this.compatibilityStatus = compatibilityStatus;
        this.compatibilityMessages = compatibilityMessages;
        this.summary = summary;
        this.content = content;
        this.source = source;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getBudget() {
        return budget;
    }

    public String getScenario() {
        return scenario;
    }

    public String getNote() {
        return note == null ? "" : note;
    }

    public String getPartIds() {
        return partIds == null ? "" : partIds;
    }

    public String getPartSelections() {
        return partSelections == null ? "" : partSelections;
    }

    public Integer getTotalPrice() {
        return totalPrice == null ? 0 : totalPrice;
    }

    public Integer getPerformanceScore() {
        return performanceScore == null ? 0 : performanceScore;
    }

    public String getCompatibilityStatus() {
        return compatibilityStatus == null ? "WARN" : compatibilityStatus;
    }

    public String getCompatibilityMessages() {
        return compatibilityMessages == null ? "" : compatibilityMessages;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public BuildConfigSource getSource() {
        return source;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
