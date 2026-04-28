package cn.cctstudio.smartdiypcassistantbackend.notification;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.notification.dto.NotificationResponse;
import cn.cctstudio.smartdiypcassistantbackend.notification.dto.UnreadNotificationCountResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.list(principal.userId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadNotificationCountResponse> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(new UnreadNotificationCountResponse(notificationService.unreadCount(principal.userId())));
    }

    @GetMapping("/{id}")
    public ApiResponse<NotificationResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(notificationService.detail(principal.userId(), id));
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.userId());
        return ApiResponse.ok();
    }
}
