package io.github.qq3939.codenest.service.outbox.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import io.github.qq3939.codenest.api.enums.DeadLetterStatus;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("dead_letter_message")
public class DeadLetterMessageDO extends BaseDO {
    private String msgId;
    private String queue;
    private String payload;
    private String error;
    private LocalDateTime failedTime;
    private DeadLetterStatus status;
}
