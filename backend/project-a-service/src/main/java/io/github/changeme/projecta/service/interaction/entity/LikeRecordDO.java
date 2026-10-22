package io.github.changeme.projecta.service.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import io.github.changeme.projecta.api.enums.TargetType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("like_record")
public class LikeRecordDO extends BaseDO {
    private Long userId;
    private TargetType targetType;
    private Long targetId;
    /** 1 有效，0 已取消 */
    private Integer status;
}
