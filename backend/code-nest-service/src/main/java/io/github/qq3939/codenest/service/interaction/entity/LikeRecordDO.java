package io.github.qq3939.codenest.service.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import io.github.qq3939.codenest.api.enums.TargetType;
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
