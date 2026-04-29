package cn.cctstudio.smartdiypcassistantbackend.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_build_task")
public class AiBuildTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long conversationId;

    private Long attachedBuildId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String requestMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiBuildTaskStatus status;

    @Column(nullable = false)
    private Integer progress;

    @Column(nullable = false, length = 40)
    private String stageTitle;

    @Column(nullable = false, length = 160)
    private String stageDescription;

    @Column(nullable = false, length = 240)
    private String userFacingMessage;

    private Long buildConfigId;

    @Column(length = 80)
    private String buildConfigTitle;

    @Column(length = 20)
    private String buildConfigSerialNumber;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime finishedAt;

    protected AiBuildTask() {
    }

    public AiBuildTask(Long userId, Long conversationId, Long attachedBuildId, String requestMessage) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.attachedBuildId = attachedBuildId;
        this.requestMessage = requestMessage == null ? "" : requestMessage;
        this.status = AiBuildTaskStatus.QUEUED;
        this.progress = 5;
        this.stageTitle = "排队中";
        this.stageDescription = "正在准备京东商品检索";
        this.userFacingMessage = "已开始生成京东可购买配置单";
        this.buildConfigTitle = "";
        this.buildConfigSerialNumber = "";
        this.errorMessage = "";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getAttachedBuildId() {
        return attachedBuildId;
    }

    public String getRequestMessage() {
        return requestMessage == null ? "" : requestMessage;
    }

    public AiBuildTaskStatus getStatus() {
        return status == null ? AiBuildTaskStatus.QUEUED : status;
    }

    public Integer getProgress() {
        return progress == null ? 0 : progress;
    }

    public String getStageTitle() {
        return stageTitle == null ? "" : stageTitle;
    }

    public String getStageDescription() {
        return stageDescription == null ? "" : stageDescription;
    }

    public String getUserFacingMessage() {
        return userFacingMessage == null ? "" : userFacingMessage;
    }

    public Long getBuildConfigId() {
        return buildConfigId;
    }

    public String getBuildConfigTitle() {
        return buildConfigTitle == null ? "" : buildConfigTitle;
    }

    public String getBuildConfigSerialNumber() {
        return buildConfigSerialNumber == null ? "" : buildConfigSerialNumber;
    }

    public String getErrorMessage() {
        return errorMessage == null ? "" : errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void markRunning(int progress, String stageTitle, String stageDescription) {
        this.status = AiBuildTaskStatus.RUNNING;
        updateProgress(progress, stageTitle, stageDescription);
    }

    public void updateProgress(int progress, String stageTitle, String stageDescription) {
        this.progress = Math.max(0, Math.min(99, progress));
        this.stageTitle = clean(stageTitle, 40);
        this.stageDescription = clean(stageDescription, 160);
        this.userFacingMessage = this.stageTitle + "：" + this.stageDescription;
        this.updatedAt = LocalDateTime.now();
    }

    public void markSucceeded(Long buildConfigId, String buildConfigTitle, String serialNumber) {
        this.status = AiBuildTaskStatus.SUCCEEDED;
        this.progress = 100;
        this.stageTitle = "生成完成";
        this.stageDescription = "已保存到配置页";
        this.userFacingMessage = "京东可购买配置单已生成";
        this.buildConfigId = buildConfigId;
        this.buildConfigTitle = clean(buildConfigTitle, 80);
        this.buildConfigSerialNumber = clean(serialNumber, 20);
        this.updatedAt = LocalDateTime.now();
        this.finishedAt = this.updatedAt;
    }

    public void markFailed(String errorMessage) {
        this.status = AiBuildTaskStatus.FAILED;
        this.progress = 100;
        this.stageTitle = "生成失败";
        this.stageDescription = clean(errorMessage, 160);
        this.userFacingMessage = "京东可购买配置单没有生成成功";
        this.errorMessage = clean(errorMessage, 2000);
        this.updatedAt = LocalDateTime.now();
        this.finishedAt = this.updatedAt;
    }

    private String clean(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
