package cn.cctstudio.smartdiypcassistantbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "不能为空") @Size(min = 3, max = 32, message = "长度需要在 3 到 32 位之间") String username,
        @NotBlank(message = "不能为空") @Email(message = "格式不正确") String email,
        @NotBlank(message = "不能为空")
        @Size(min = 8, max = 20, message = "长度需要在 8 到 20 位之间")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "必须包含字母和数字"
        )
        String password,
        @NotBlank(message = "不能为空") @Size(min = 6, max = 6, message = "验证码必须为 6 位") String verificationCode
) {
}
