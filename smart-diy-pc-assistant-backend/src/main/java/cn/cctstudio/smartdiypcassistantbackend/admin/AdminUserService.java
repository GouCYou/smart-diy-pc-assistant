package cn.cctstudio.smartdiypcassistantbackend.admin;

import cn.cctstudio.smartdiypcassistantbackend.admin.dto.AdminUserEnabledRequest;
import cn.cctstudio.smartdiypcassistantbackend.admin.dto.AdminUserResponse;
import cn.cctstudio.smartdiypcassistantbackend.admin.dto.AdminUserRoleRequest;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRole;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final MediaUrlService mediaUrlService;

    public AdminUserService(UserRepository userRepository, MediaUrlService mediaUrlService) {
        this.userRepository = userRepository;
        this.mediaUrlService = mediaUrlService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse updateRole(Long id, AdminUserRoleRequest request, Long operatorId) {
        if (id.equals(operatorId)) {
            throw new BusinessException("不能修改自己的后台权限");
        }
        User user = findUser(id);
        user.changeRole(parseRole(request.role()));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse updateEnabled(Long id, AdminUserEnabledRequest request, Long operatorId) {
        if (id.equals(operatorId) && !request.enabled()) {
            throw new BusinessException("不能禁用当前登录账号");
        }
        User user = findUser(id);
        if (request.enabled()) {
            user.changeEnabled(true);
        } else {
            user.banUntil(request.banExpiresAt());
        }
        return toResponse(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private UserRole parseRole(String role) {
        try {
            return UserRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException("角色不正确");
        }
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                mediaUrlService.toPublicUrl(user.getAvatarKey()),
                user.getBio(),
                user.getRole().name(),
                !user.isCurrentlyBanned(),
                user.getBanExpiresAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
