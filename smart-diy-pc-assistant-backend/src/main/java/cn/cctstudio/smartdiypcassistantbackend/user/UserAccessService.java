package cn.cctstudio.smartdiypcassistantbackend.user;

import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Service
public class UserAccessService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository userRepository;

    public UserAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void requireAvailable(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "登录状态已过期，请重新登录"));
        user.clearExpiredBan();
        if (user.isCurrentlyBanned()) {
            throw new BusinessException(403, banMessage(user));
        }
    }

    public String banMessage(User user) {
        if (user.getBanExpiresAt() == null) {
            return "该账户已被封禁，解封时间：永久";
        }
        return "该账户已被封禁，解封时间：" + user.getBanExpiresAt().format(FORMATTER);
    }
}
