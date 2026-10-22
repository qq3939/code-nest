package io.github.qq3939.codenest.service.outbox.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import io.github.qq3939.codenest.api.enums.OutboxStatus;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("outbox_message")
public class OutboxMessageDO extends BaseDO {
    private String msgId;
    private String exchange;
    private String routingKey;
    private String payload;
    private OutboxStatus status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
}
