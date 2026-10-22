package io.github.qq3939.codenest.service.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
@TableName("user_stat")
public class UserStatDO {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Long articleCount;
    private Long followerCount;
    private Long followingCount;
    private Long likeReceived;
    private Long readReceived;
}
