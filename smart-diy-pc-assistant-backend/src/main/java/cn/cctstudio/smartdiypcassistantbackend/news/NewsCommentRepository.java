package cn.cctstudio.smartdiypcassistantbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsCommentRepository extends JpaRepository<NewsComment, Long> {
    List<NewsComment> findByNewsIdAndVisibleTrueOrderByCreatedAtAsc(Long newsId);

    List<NewsComment> findByNewsIdOrderByCreatedAtAsc(Long newsId);

    long countByNewsIdAndVisibleTrue(Long newsId);

    void deleteByNewsId(Long newsId);
}
