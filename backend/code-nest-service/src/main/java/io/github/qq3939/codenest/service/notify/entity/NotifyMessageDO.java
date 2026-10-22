package io.github.qq3939.codenest.service.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import io.github.qq3939.codenest.api.enums.NotifyType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("notify_message")
public class NotifyMessageDO extends BaseDO {
    private Long receiverId;
    private Long senderId;
    private NotifyType type;
    private String targetType;
    private Long targetId;
    private String content;
    private Integer isRead;
    private String msgId;
}
