package cn.cctstudio.smartdiypcassistantbackend.security;

import cn.cctstudio.smartdiypcassistantbackend.user.UserRole;

public record UserPrincipal(Long userId, String username, UserRole role) {
}
