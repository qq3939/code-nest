package io.github.changeme.projecta.api.dto;

import io.github.changeme.projecta.api.enums.SensitiveLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SensitiveWordSaveReq(@NotBlank @Size(max = 64) String word, @NotNull SensitiveLevel level, boolean enabled) {
}
