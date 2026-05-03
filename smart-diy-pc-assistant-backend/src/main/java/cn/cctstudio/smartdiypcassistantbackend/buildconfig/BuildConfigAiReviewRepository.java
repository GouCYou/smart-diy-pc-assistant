package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuildConfigAiReviewRepository extends JpaRepository<BuildConfigAiReview, Long> {

    Optional<BuildConfigAiReview> findByBuildConfigIdAndUserId(Long buildConfigId, Long userId);

    void deleteByBuildConfigId(Long buildConfigId);
}
