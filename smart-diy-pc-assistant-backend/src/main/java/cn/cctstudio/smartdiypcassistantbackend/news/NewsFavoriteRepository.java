package cn.cctstudio.smartdiypcassistantbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsFavoriteRepository extends JpaRepository<NewsFavorite, Long> {
    long countByNewsId(Long newsId);

    boolean existsByNewsIdAndUserId(Long newsId, Long userId);

    Optional<NewsFavorite> findByNewsIdAndUserId(Long newsId, Long userId);

    List<NewsFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByNewsId(Long newsId);
}
