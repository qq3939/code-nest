package io.github.changeme.projecta.api.vo;

import java.time.LocalDateTime;

public record NotifyVO(Long id, String type, UserBriefVO sender, String targetType, Long targetId, String content,
                       boolean read, LocalDateTime createTime) {
}
