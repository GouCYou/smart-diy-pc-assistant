package cn.cctstudio.smartdiypcassistantbackend.news;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.LocalDateTime;
public interface ImportedNewsRepository extends JpaRepository<ImportedNews,Long>{
 boolean existsBySourceUrl(String url);
 List<ImportedNews> findByPublishedAtBefore(LocalDateTime cutoff);
}
