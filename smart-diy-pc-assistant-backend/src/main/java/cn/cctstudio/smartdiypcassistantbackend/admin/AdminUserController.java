package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.admin.dto.AdminUserEnabledRequest;
import cn.cctstudio.smartdiypcassistantbackend.admin.dto.AdminUserResponse;
import cn.cctstudio.smartdiypcassistantbackend.admin.dto.AdminUserRoleRequest;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> list() {
        return ApiResponse.ok(adminUserService.list());
    }

    @PutMapping("/{id}/role")
    public ApiResponse<AdminUserResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(adminUserService.updateRole(id, request, principal.userId()));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<AdminUserResponse> updateEnabled(
            @PathVariable Long id,
            @RequestBody AdminUserEnabledRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(adminUserService.updateEnabled(id, request, principal.userId()));
    }
}
