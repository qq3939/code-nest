package io.github.qq3939.codenest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagSaveReq(@NotBlank @Size(max = 32) String name) {
}
