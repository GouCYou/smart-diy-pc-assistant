package cn.cctstudio.smartdiypcassistantbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsCommentLikeRepository extends JpaRepository<NewsCommentLike, Long> {
    long countByCommentId(Long commentId);

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    Optional<NewsCommentLike> findByCommentIdAndUserId(Long commentId, Long userId);

    void deleteByCommentId(Long commentId);
}
