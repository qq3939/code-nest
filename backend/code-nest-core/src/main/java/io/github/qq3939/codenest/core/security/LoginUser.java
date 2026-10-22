package io.github.qq3939.codenest.core.security;

import io.github.qq3939.codenest.api.enums.UserRole;

public record LoginUser(Long userId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
