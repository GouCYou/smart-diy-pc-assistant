package cn.cctstudio.smartdiypcassistantbackend.auth;

import cn.cctstudio.smartdiypcassistantbackend.auth.dto.CurrentUserResponse;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.LoginRequest;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.LoginResponse;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.RegisterRequest;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.ResetPasswordRequest;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.file.MediaUrlService;
import cn.cctstudio.smartdiypcassistantbackend.security.JwtService;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import cn.cctstudio.smartdiypcassistantbackend.user.User;
import cn.cctstudio.smartdiypcassistantbackend.user.UserAccessService;
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
    private final MediaUrlService mediaUrlService;
    private final EmailVerificationService emailVerificationService;
    private final UserAccessService userAccessService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MediaUrlService mediaUrlService,
            EmailVerificationService emailVerificationService,
            UserAccessService userAccessService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mediaUrlService = mediaUrlService;
        this.emailVerificationService = emailVerificationService;
        this.userAccessService = userAccessService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        bootstrapDefaultAdmin();
        User user = authenticate(request);
        return createLoginResponse(user);
    }

    @Transactional
    public LoginResponse adminLogin(LoginRequest request) {
        bootstrapDefaultAdmin();
        User user = authenticate(request);
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(403, "该账号没有后台管理权限");
        }
        return createLoginResponse(user);
    }

    private User authenticate(LoginRequest request) {
        String account = request.username().trim();
        User user = userRepository.findByUsernameOrEmail(account, account.toLowerCase())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        user.clearExpiredBan();
        if (user.isCurrentlyBanned()) {
            throw new BusinessException(403, userAccessService.banMessage(user));
        }
        return user;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        bootstrapDefaultAdmin();
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException("用户名已存在");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException("邮箱已被绑定");
        }
        if (userRepository.findByNickname(username).isPresent()) {
            throw new BusinessException("昵称已存在");
        }
        emailVerificationService.verifyRegisterCodeAndConsume(email, request.verificationCode());
        User user = userRepository.save(new User(
                username,
                email,
                passwordEncoder.encode(request.password()),
                UserRole.USER
        ));
        return createLoginResponse(user);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        bootstrapDefaultAdmin();
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("该邮箱未注册"));
        emailVerificationService.verifyResetPasswordCode(email, request.verificationCode());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("新密码不能和原密码相同");
        }
        emailVerificationService.consumeResetPasswordCode(email);
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "登录状态已过期，请重新登录"));
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarKey(),
                mediaUrlService.toPublicUrl(user.getAvatarKey()),
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
                user.getAvatarKey(),
                mediaUrlService.toPublicUrl(user.getAvatarKey()),
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
