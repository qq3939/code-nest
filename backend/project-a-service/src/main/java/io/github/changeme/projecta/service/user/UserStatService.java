package io.github.changeme.projecta.service.user;

import io.github.changeme.projecta.api.vo.UserStatVO;
import io.github.changeme.projecta.service.user.mapper.UserStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * user_stat 的维护。文章数、粉丝数、关注数随业务操作原子增减；
 * 获赞数、阅读数是 article_stat 的汇总，打开个人主页时现算现存（见 D-009）。
 */
@Service
@RequiredArgsConstructor
public class UserStatService {
    private final UserStatMapper userStatMapper;

    public void increaseArticle(Long userId, long delta) {
        userStatMapper.increase(userId, "article", delta);
    }

    public void increaseFollower(Long userId, long delta) {
        userStatMapper.increase(userId, "follower", delta);
    }

    public void increaseFollowing(Long userId, long delta) {
        userStatMapper.increase(userId, "following", delta);
    }

    public long followerCount(Long userId) {
        var stat = userStatMapper.selectById(userId);
        return stat == null || stat.getFollowerCount() == null ? 0 : stat.getFollowerCount();
    }

    public UserStatVO refreshAndGet(Long userId) {
        userStatMapper.refreshReceived(userId);
        return UserConverter.toStatVO(userStatMapper.selectById(userId));
    }
}
