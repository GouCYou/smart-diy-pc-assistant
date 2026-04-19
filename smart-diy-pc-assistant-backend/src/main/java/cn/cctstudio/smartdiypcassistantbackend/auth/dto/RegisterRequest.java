package cn.cctstudio.smartdiypcassistantbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "不能为空") @Size(min = 3, max = 32, message = "长度需要在 3 到 32 位之间") String username,
        @NotBlank(message = "不能为空") @Email(message = "格式不正确") String email,
        @NotBlank(message = "不能为空") @Size(min = 6, max = 64, message = "长度需要在 6 到 64 位之间") String password
) {
}
