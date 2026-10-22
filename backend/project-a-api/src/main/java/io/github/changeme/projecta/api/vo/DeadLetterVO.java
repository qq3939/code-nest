package io.github.changeme.projecta.api.vo;

import java.time.LocalDateTime;

public record DeadLetterVO(Long id, String msgId, String queue, String payload, String error, LocalDateTime failedTime,
                           String status) {
}
