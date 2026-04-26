package cn.cctstudio.smartdiypcassistantbackend.auth;

import cn.cctstudio.smartdiypcassistantbackend.auth.dto.CaptchaResponse;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.LoginRequest;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.LoginResponse;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.CurrentUserResponse;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.RegisterRequest;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.ResetPasswordRequest;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.SendVerificationCodeRequest;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.VerificationCodeResponse;
import cn.cctstudio.smartdiypcassistantbackend.common.ApiResponse;
import cn.cctstudio.smartdiypcassistantbackend.config.properties.VerificationCodeProperties;
import cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final HumanCaptchaService humanCaptchaService;
    private final VerificationCodeProperties verificationCodeProperties;

    public AuthController(
            AuthService authService,
            EmailVerificationService emailVerificationService,
            HumanCaptchaService humanCaptchaService,
            VerificationCodeProperties verificationCodeProperties
    ) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.humanCaptchaService = humanCaptchaService;
        this.verificationCodeProperties = verificationCodeProperties;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/admin-login")
    public ApiResponse<LoginResponse> adminLogin(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.adminLogin(request));
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.ok(humanCaptchaService.createCaptcha());
    }

    @PostMapping("/verification-code")
    public ApiResponse<VerificationCodeResponse> sendVerificationCode(
            @Valid @RequestBody SendVerificationCodeRequest request
    ) {
        humanCaptchaService.verify(request.captchaId(), request.captchaCode());
        emailVerificationService.sendRegisterCode(request.email());
        return ApiResponse.ok(new VerificationCodeResponse(
                verificationCodeProperties.resendSeconds(),
                verificationCodeProperties.expireMinutes()
        ));
    }

    @PostMapping("/password-reset/verification-code")
    public ApiResponse<VerificationCodeResponse> sendPasswordResetVerificationCode(
            @Valid @RequestBody SendVerificationCodeRequest request
    ) {
        humanCaptchaService.verify(request.captchaId(), request.captchaCode());
        emailVerificationService.sendResetPasswordCode(request.email());
        return ApiResponse.ok(new VerificationCodeResponse(
                verificationCodeProperties.resendSeconds(),
                verificationCodeProperties.expireMinutes()
        ));
    }

    @PostMapping("/password-reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(authService.currentUser(principal.userId()));
    }
}
