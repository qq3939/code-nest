package io.github.qq3939.codenest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileReq(
        @NotBlank @Size(max = 32) String nickname,
        @Size(max = 255) String bio,
        @Size(max = 255) String avatarUrl) {
}
