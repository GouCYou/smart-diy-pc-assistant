package cn.cctstudio.smartdiypcassistantbackend.feedback;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.feedback.dto.FeedbackRequest;
import cn.cctstudio.smartdiypcassistantbackend.feedback.dto.FeedbackResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ApiResponse<FeedbackResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FeedbackRequest request
    ) {
        return ApiResponse.ok(feedbackService.create(principal.userId(), request));
    }

    @GetMapping("/mine")
    public ApiResponse<List<FeedbackResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(feedbackService.listMine(principal.userId()));
    }
}
