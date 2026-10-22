package io.github.qq3939.codenest.api.vo;

import java.time.LocalDateTime;

/** 个人主页头部。followed 表示当前登录用户是否已关注此人，未登录时为 false。 */
public record UserProfileVO(UserBriefVO user, UserStatVO stat, boolean followed, LocalDateTime joinTime) {
}
