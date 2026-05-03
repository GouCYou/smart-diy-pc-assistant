package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.feedback.FeedbackService;
import cn.cctstudio.smartdiypcassistantbackend.feedback.dto.FeedbackResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feedback")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ApiResponse<List<FeedbackResponse>> list() {
        return ApiResponse.ok(feedbackService.adminList());
    }

    @PatchMapping("/{id}/received")
    public ApiResponse<FeedbackResponse> markReceived(@PathVariable Long id) {
        return ApiResponse.ok(feedbackService.markReceived(id));
    }

    @PatchMapping("/{id}/resolved")
    public ApiResponse<FeedbackResponse> markResolved(@PathVariable Long id) {
        return ApiResponse.ok(feedbackService.markResolved(id));
    }
}
