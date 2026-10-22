package io.github.changeme.projecta.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterReq(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "用户名只能是 4–32 位字母、数字或下划线") String username,
        @NotBlank @Size(min = 8, max = 64, message = "密码长度 8–64 位") String password,
        @Size(max = 32) String nickname) {
}
