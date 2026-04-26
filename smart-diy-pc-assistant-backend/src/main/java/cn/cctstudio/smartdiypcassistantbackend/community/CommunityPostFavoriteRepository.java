package cn.cctstudio.smartdiypcassistantbackend.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityPostFavoriteRepository extends JpaRepository<CommunityPostFavorite, Long> {
    long countByPostId(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<CommunityPostFavorite> findByPostIdAndUserId(Long postId, Long userId);

    List<CommunityPostFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByPostId(Long postId);
}
