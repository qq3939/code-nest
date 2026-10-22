package io.github.qq3939.codenest.api.vo;

import java.time.LocalDateTime;

public record AuditLogVO(Long id, UserBriefVO admin, String action, String targetType, Long targetId, String reason,
                         LocalDateTime createTime) {
}
