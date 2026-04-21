package cn.cctstudio.smartdiypcassistantbackend.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityCommentLikeRepository extends JpaRepository<CommunityCommentLike, Long> {
    long countByCommentId(Long commentId);

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    Optional<CommunityCommentLike> findByCommentIdAndUserId(Long commentId, Long userId);

    void deleteByCommentId(Long commentId);
}
