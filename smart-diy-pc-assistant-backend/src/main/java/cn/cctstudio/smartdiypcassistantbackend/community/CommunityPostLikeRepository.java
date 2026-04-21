package cn.cctstudio.smartdiypcassistantbackend.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {
    long countByPostId(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<CommunityPostLike> findByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostId(Long postId);
}
