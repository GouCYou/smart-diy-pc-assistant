package cn.cctstudio.smartdiypcassistantbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendVerificationCodeRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        @NotBlank(message = "请先完成人机验证") String captchaId,
        @NotBlank(message = "请输入验证码") String captchaCode
) {
}
