package cn.cctstudio.smartdiypcassistantbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsLikeRepository extends JpaRepository<NewsLike, Long> {
    long countByNewsId(Long newsId);

    boolean existsByNewsIdAndUserId(Long newsId, Long userId);

    Optional<NewsLike> findByNewsIdAndUserId(Long newsId, Long userId);

    void deleteByNewsId(Long newsId);
}
