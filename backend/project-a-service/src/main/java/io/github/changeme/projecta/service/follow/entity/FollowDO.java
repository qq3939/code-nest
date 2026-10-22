package io.github.changeme.projecta.service.follow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("follow")
public class FollowDO extends BaseDO {
    private Long followerId;
    private Long followeeId;
    private Integer status;
}
