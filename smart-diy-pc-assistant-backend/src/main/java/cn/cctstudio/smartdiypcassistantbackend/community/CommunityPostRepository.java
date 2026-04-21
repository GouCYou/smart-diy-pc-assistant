package cn.cctstudio.smartdiypcassistantbackend.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    List<CommunityPost> findByVisibleTrueOrderByCreatedAtDesc();

    List<CommunityPost> findAllByOrderByCreatedAtDesc();

    Optional<CommunityPost> findByIdAndVisibleTrue(Long id);
}
