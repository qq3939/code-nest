package io.github.changeme.projecta.api.vo;

/** 当前登录用户自己的信息，比 UserBriefVO 多了 username 和 role。 */
public record UserVO(Long id, String username, String nickname, String avatarUrl, String bio, String role) {
}
