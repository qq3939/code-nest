package io.github.changeme.projecta.service.follow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.api.common.PageResult;
import io.github.changeme.projecta.api.event.EventTypes;
import io.github.changeme.projecta.api.event.Payloads;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.service.common.AfterCommit;
import io.github.changeme.projecta.service.follow.entity.FollowDO;
import io.github.changeme.projecta.service.follow.mapper.FollowMapper;
import io.github.changeme.projecta.service.interaction.RelationSwitch;
import io.github.changeme.projecta.service.outbox.OutboxService;
import io.github.changeme.projecta.service.user.UserService;
import io.github.changeme.projecta.service.user.UserStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {
    private final FollowMapper followMapper;
    private final UserService userService;
    private final UserStatService userStatService;
    private final OutboxService outboxService;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    @Transactional(rollbackFor = Exception.class)
    public void follow(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new BizException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        userService.require(targetId);
        if (!RelationSwitch.activate(followMapper, find(userId, targetId), () -> newRecord(userId, targetId),
                followMapper::switchStatus)) {
            return;
        }
        userStatService.increaseFollower(targetId, 1);
        userStatService.increaseFollowing(userId, 1);
        outboxService.save(EventTypes.USER_FOLLOWED, new Payloads.FollowEvent(userId, targetId, true));
        AfterCommit.run(() -> redis.delete(keys.feedBigVFollow(userId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long userId, Long targetId) {
        if (!RelationSwitch.deactivate(find(userId, targetId), followMapper::switchStatus)) {
            return;
        }
        userStatService.increaseFollower(targetId, -1);
        userStatService.increaseFollowing(userId, -1);
        outboxService.save(EventTypes.USER_FOLLOWED, new Payloads.FollowEvent(userId, targetId, false));
        AfterCommit.run(() -> redis.delete(keys.feedBigVFollow(userId)));
    }

    public boolean isFollowing(Long userId, Long targetId) {
        if (userId == null || userId.equals(targetId)) {
            return false;
        }
        FollowDO row = find(userId, targetId);
        return row != null && row.getStatus() == 1;
    }

    /** authorIds 中当前用户仍在关注的那些，Feed 读时过滤"已取关作者"用。 */
    public Set<Long> followedAmong(Long userId, Collection<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Set.of();
        }
        return followMapper.selectList(Wrappers.<FollowDO>lambdaQuery()
                        .select(FollowDO::getFolloweeId)
                        .eq(FollowDO::getFollowerId, userId)
                        .in(FollowDO::getFolloweeId, authorIds)
                        .eq(FollowDO::getStatus, 1))
                .stream().map(FollowDO::getFolloweeId).collect(Collectors.toCollection(HashSet::new));
    }

    public List<Long> followingIds(Long userId) {
        return followMapper.selectList(Wrappers.<FollowDO>lambdaQuery()
                        .select(FollowDO::getFolloweeId)
                        .eq(FollowDO::getFollowerId, userId)
                        .eq(FollowDO::getStatus, 1))
                .stream().map(FollowDO::getFolloweeId).toList();
    }

    public List<FollowDO> followersAfter(Long userId, Long afterId, int limit) {
        return followMapper.selectFollowersAfter(userId, afterId, limit);
    }

    public List<Long> followedBigV(Long userId, int threshold) {
        return followMapper.selectFollowedBigV(userId, threshold);
    }

    public PageResult<UserBriefVO> followers(Long userId, long page, long size) {
        Page<FollowDO> result = followMapper.selectPage(Page.of(page, size), Wrappers.<FollowDO>lambdaQuery()
                .eq(FollowDO::getFolloweeId, userId).eq(FollowDO::getStatus, 1).orderByDesc(FollowDO::getUpdateTime));
        return toUserPage(result, FollowDO::getFollowerId, page, size);
    }

    public PageResult<UserBriefVO> followings(Long userId, long page, long size) {
        Page<FollowDO> result = followMapper.selectPage(Page.of(page, size), Wrappers.<FollowDO>lambdaQuery()
                .eq(FollowDO::getFollowerId, userId).eq(FollowDO::getStatus, 1).orderByDesc(FollowDO::getUpdateTime));
        return toUserPage(result, FollowDO::getFolloweeId, page, size);
    }

    private PageResult<UserBriefVO> toUserPage(Page<FollowDO> result, Function<FollowDO, Long> idOf, long page, long size) {
        List<Long> ids = result.getRecords().stream().map(idOf).toList();
        Map<Long, UserBriefVO> users = userService.briefs(ids);
        return new PageResult<>(ids.stream().map(users::get).toList(), result.getTotal(), page, size);
    }

    private FollowDO find(Long followerId, Long followeeId) {
        return followMapper.selectOne(Wrappers.<FollowDO>lambdaQuery()
                .eq(FollowDO::getFollowerId, followerId)
                .eq(FollowDO::getFolloweeId, followeeId));
    }

    private static FollowDO newRecord(Long followerId, Long followeeId) {
        FollowDO row = new FollowDO();
        row.setFollowerId(followerId);
        row.setFolloweeId(followeeId);
        row.setStatus(1);
        return row;
    }
}
