package io.github.changeme.projecta.service.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import io.github.changeme.projecta.api.enums.UserRole;
import io.github.changeme.projecta.api.enums.UserStatus;
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
