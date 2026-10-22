package io.github.changeme.projecta.service.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import io.github.changeme.projecta.api.enums.NotifyType;
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
