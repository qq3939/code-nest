package io.github.changeme.projecta.core.security;

import io.github.changeme.projecta.api.enums.UserRole;

public record LoginUser(Long userId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
