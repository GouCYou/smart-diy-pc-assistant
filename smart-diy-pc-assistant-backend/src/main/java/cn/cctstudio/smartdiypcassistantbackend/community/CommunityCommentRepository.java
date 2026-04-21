package cn.cctstudio.smartdiypcassistantbackend.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
    List<CommunityComment> findByPostIdAndVisibleTrueOrderByCreatedAtAsc(Long postId);

    List<CommunityComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    long countByPostIdAndVisibleTrue(Long postId);

    void deleteByPostId(Long postId);
}
