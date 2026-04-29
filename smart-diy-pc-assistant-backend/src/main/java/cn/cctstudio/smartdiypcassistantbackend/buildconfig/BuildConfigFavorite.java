package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "pc_build_config_favorite",
        uniqueConstraints = @UniqueConstraint(name = "uk_build_config_favorite_user_build", columnNames = {"user_id", "build_config_id"}),
        indexes = {
                @Index(name = "idx_build_config_favorite_user", columnList = "user_id"),
                @Index(name = "idx_build_config_favorite_build", columnList = "build_config_id")
        }
)
public class BuildConfigFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long buildConfigId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected BuildConfigFavorite() {
    }

    public BuildConfigFavorite(Long userId, Long buildConfigId) {
        this.userId = userId;
        this.buildConfigId = buildConfigId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBuildConfigId() {
        return buildConfigId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
