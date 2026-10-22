package io.github.qq3939.codenest.service.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("admin_audit_log")
public class AdminAuditLogDO extends BaseDO {
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
}
