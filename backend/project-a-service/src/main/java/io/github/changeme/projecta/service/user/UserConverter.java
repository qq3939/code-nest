package io.github.changeme.projecta.service.user;

import io.github.changeme.projecta.api.vo.AdminUserVO;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.api.vo.UserStatVO;
import io.github.changeme.projecta.api.vo.UserVO;
import io.github.changeme.projecta.service.user.entity.UserDO;
import io.github.changeme.projecta.service.user.entity.UserStatDO;

/**
 * DO -> VO 的静态转换。VO 在 api 模块、DO 在 service 模块，api 不能反向依赖 service，
 * 所以 SPEC 里"ArticleVO.from(...)"这类静态方法统一放在各业务包的 XxxConverter 中（见 D-004）。
 */
public final class UserConverter {

    private UserConverter() {
    }

    public static UserVO toVO(UserDO u) {
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(), u.getBio(), u.getRole().name());
    }

    public static UserBriefVO toBrief(UserDO u) {
        return new UserBriefVO(u.getId(), u.getNickname(), u.getAvatarUrl(), u.getBio());
    }

    public static AdminUserVO toAdminVO(UserDO u) {
        return new AdminUserVO(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(), u.getRole().name(),
                u.getStatus().name(), u.getCreateTime());
    }

    public static UserStatVO toStatVO(UserStatDO s) {
        if (s == null) {
            return UserStatVO.zero();
        }
        return new UserStatVO(nz(s.getArticleCount()), nz(s.getFollowerCount()), nz(s.getFollowingCount()),
                nz(s.getLikeReceived()), nz(s.getReadReceived()));
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
