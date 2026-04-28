package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.notification.NotificationService;
import cn.cctstudio.smartdiypcassistantbackend.notification.dto.AdminSystemNotificationRequest;
import cn.cctstudio.smartdiypcassistantbackend.notification.dto.AdminSystemNotificationResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/system")
    public ApiResponse<AdminSystemNotificationResponse> sendSystemNotification(
            @Valid @RequestBody AdminSystemNotificationRequest request
    ) {
        return ApiResponse.ok(notificationService.sendSystemNotification(request));
    }
}
