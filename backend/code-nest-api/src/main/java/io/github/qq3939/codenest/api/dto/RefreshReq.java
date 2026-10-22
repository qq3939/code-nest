package io.github.qq3939.codenest.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 刷新和退出共用：都只需要刷新令牌。 */
public record RefreshReq(@NotBlank String refreshToken) {
}
