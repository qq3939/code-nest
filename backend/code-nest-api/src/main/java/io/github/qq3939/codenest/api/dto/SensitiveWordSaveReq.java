package io.github.qq3939.codenest.api.dto;

import io.github.qq3939.codenest.api.enums.SensitiveLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SensitiveWordSaveReq(@NotBlank @Size(max = 64) String word, @NotNull SensitiveLevel level, boolean enabled) {
}
