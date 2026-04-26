package cn.cctstudio.smartdiypcassistantbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserRoleRequest(@NotBlank(message = "角色不能为空") String role) {
}
