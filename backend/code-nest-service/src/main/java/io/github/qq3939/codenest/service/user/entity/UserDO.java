package io.github.qq3939.codenest.service.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import io.github.qq3939.codenest.api.enums.UserRole;
import io.github.qq3939.codenest.api.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("user")
public class UserDO extends BaseDO {
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private UserRole role;
    private UserStatus status;
}
