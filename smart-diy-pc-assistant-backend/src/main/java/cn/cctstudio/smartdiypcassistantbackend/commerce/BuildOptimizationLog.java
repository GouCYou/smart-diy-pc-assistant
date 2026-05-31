package cn.cctstudio.smartdiypcassistantbackend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "build_optimization_log")
public class BuildOptimizationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    private Integer beforeTotalPrice;

    private Integer afterTotalPrice;

    private Integer performanceChange;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String suggestion;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected BuildOptimizationLog() {
    }

    public BuildOptimizationLog(
            Long userId,
            String title,
            String reason,
            Integer beforeTotalPrice,
            Integer afterTotalPrice,
            Integer performanceChange,
            String suggestion
    ) {
        this.userId = userId;
        this.title = clean(title, 120);
        this.reason = reason == null ? "" : reason.trim();
        this.beforeTotalPrice = beforeTotalPrice == null ? 0 : beforeTotalPrice;
        this.afterTotalPrice = afterTotalPrice == null ? 0 : afterTotalPrice;
        this.performanceChange = performanceChange == null ? 0 : performanceChange;
        this.suggestion = suggestion == null ? "" : suggestion.trim();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public String getReason() {
        return reason == null ? "" : reason;
    }

    public Integer getBeforeTotalPrice() {
        return beforeTotalPrice == null ? 0 : beforeTotalPrice;
    }

    public Integer getAfterTotalPrice() {
        return afterTotalPrice == null ? 0 : afterTotalPrice;
    }

    public Integer getPerformanceChange() {
        return performanceChange == null ? 0 : performanceChange;
    }

    public String getSuggestion() {
        return suggestion == null ? "" : suggestion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
