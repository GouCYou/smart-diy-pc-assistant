package cn.cctstudio.smartdiypcassistantbackend.ai;

import cn.cctstudio.smartdiypcassistantbackend.ai.dto.AiBuildTaskResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatRequest;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ChatMessageResponse;
import cn.cctstudio.smartdiypcassistantbackend.ai.dto.ConversationResponse;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequest request
    ) {
        return ApiResponse.ok(aiService.chat(principal.userId(), request));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationResponse>> conversations(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(aiService.listConversations(principal.userId()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<ChatMessageResponse>> messages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long conversationId
    ) {
        return ApiResponse.ok(aiService.listMessages(principal.userId(), conversationId));
    }

    @GetMapping("/build-tasks/active")
    public ApiResponse<List<AiBuildTaskResponse>> activeBuildTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long conversationId
    ) {
        return ApiResponse.ok(aiService.listActiveBuildTasks(principal.userId(), conversationId));
    }

    @GetMapping("/build-tasks/{taskId}")
    public ApiResponse<AiBuildTaskResponse> buildTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId
    ) {
        return ApiResponse.ok(aiService.getBuildTask(principal.userId(), taskId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long conversationId
    ) {
        aiService.deleteConversation(principal.userId(), conversationId);
        return ApiResponse.ok();
    }
}
