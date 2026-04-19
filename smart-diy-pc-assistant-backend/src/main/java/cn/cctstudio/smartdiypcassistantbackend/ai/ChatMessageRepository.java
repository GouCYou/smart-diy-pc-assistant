package cn.cctstudio.smartdiypcassistantbackend.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<ChatMessage> findTop12ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
