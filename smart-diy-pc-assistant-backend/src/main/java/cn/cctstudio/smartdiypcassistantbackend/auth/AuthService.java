package cn.cctstudio.smartdiypcassistantbackend.auth;

import cn.cctstudio.smartdiypcassistantbackend.auth.dto.CurrentUserResponse;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.LoginRequest;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.LoginResponse;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.RegisterRequest;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.security.JwtService;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        bootstrapDefaultAdmin();
        String account = request.username().trim();
        User user = userRepository.findByUsernameOrEmail(account, account.toLowerCase())
                .orElseThrow(() -> new BusinessException(401, "账号或密码错误"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        return createLoginResponse(user);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        bootstrapDefaultAdmin();
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException("用户名已被使用");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException("邮箱已被绑定");
        }
        User user = userRepository.save(new User(
                username,
                email,
                passwordEncoder.encode(request.password()),
                UserRole.USER
        ));
        return createLoginResponse(user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "登录状态已失效"));
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getRole().name()
        );
    }

    private LoginResponse createLoginResponse(User user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getRole().name(),
                jwtService.createToken(principal)
        );
    }

    private void bootstrapDefaultAdmin() {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        userRepository.save(new User("admin", "admin@smart-diy.local", passwordEncoder.encode("123456"), UserRole.ADMIN));
    }
}
