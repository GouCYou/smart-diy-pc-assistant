package cn.cctstudio.smartdiypcassistantbackend.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false, length = 20)
    private String role;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    private Long buildConfigId;

    @Column(length = 80)
    private String buildConfigTitle;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String newsReferencesJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(Long conversationId, String role, String content) {
        this(conversationId, role, content, null, "");
    }

    public ChatMessage(Long conversationId, String role, String content, Long buildConfigId, String buildConfigTitle) {
        this(conversationId, role, content, buildConfigId, buildConfigTitle, "");
    }

    public ChatMessage(Long conversationId, String role, String content, Long buildConfigId, String buildConfigTitle, String newsReferencesJson) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.buildConfigId = buildConfigId;
        this.buildConfigTitle = buildConfigTitle;
        this.newsReferencesJson = newsReferencesJson;
        this.createdAt = LocalDateTime.now();
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Long getBuildConfigId() {
        return buildConfigId;
    }

    public String getBuildConfigTitle() {
        return buildConfigTitle == null ? "" : buildConfigTitle;
    }

    public String getNewsReferencesJson() {
        return newsReferencesJson == null ? "" : newsReferencesJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
