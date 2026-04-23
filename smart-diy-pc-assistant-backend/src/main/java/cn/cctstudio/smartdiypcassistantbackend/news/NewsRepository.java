package cn.cctstudio.smartdiypcassistantbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    List<News> findTop20ByStatusOrderByCreatedAtDesc(NewsStatus status);

    List<News> findByStatusOrderByCreatedAtDesc(NewsStatus status);

    List<News> findAllByOrderByCreatedAtDesc();
}
