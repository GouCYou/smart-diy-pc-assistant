package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildConfigFavoriteRepository extends JpaRepository<BuildConfigFavorite, Long> {

    List<BuildConfigFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BuildConfigFavorite> findByUserIdAndBuildConfigId(Long userId, Long buildConfigId);

    boolean existsByUserIdAndBuildConfigId(Long userId, Long buildConfigId);

    void deleteByBuildConfigId(Long buildConfigId);
}
