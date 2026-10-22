package io.github.changeme.projecta.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordReq(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, max = 64, message = "密码长度 8–64 位") String newPassword) {
}
