package io.github.changeme.projecta.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategorySaveReq(@NotBlank @Size(max = 32) String name, @NotNull Integer sort) {
}
