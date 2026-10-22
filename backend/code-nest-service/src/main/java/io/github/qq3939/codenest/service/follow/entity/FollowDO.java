package io.github.qq3939.codenest.service.follow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
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
