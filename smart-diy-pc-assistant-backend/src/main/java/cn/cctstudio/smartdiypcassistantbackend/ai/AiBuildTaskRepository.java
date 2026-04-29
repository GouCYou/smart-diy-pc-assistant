package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiBuildTaskRepository extends JpaRepository<AiBuildTask, Long> {

    Optional<AiBuildTask> findByIdAndUserId(Long id, Long userId);

    List<AiBuildTask> findByUserIdAndConversationIdAndStatusInOrderByUpdatedAtDesc(
            Long userId,
            Long conversationId,
            Collection<AiBuildTaskStatus> statuses
    );

    List<AiBuildTask> findTop5ByUserIdAndStatusInOrderByUpdatedAtDesc(
            Long userId,
            Collection<AiBuildTaskStatus> statuses
    );

    List<AiBuildTask> findByStatusInOrderByUpdatedAtAsc(Collection<AiBuildTaskStatus> statuses);
}
