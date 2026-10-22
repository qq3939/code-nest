package io.github.changeme.projecta.api.vo;

import java.time.LocalDateTime;

public record AdminUserVO(Long id, String username, String nickname, String avatarUrl, String role, String status,
                          LocalDateTime createTime) {
}
