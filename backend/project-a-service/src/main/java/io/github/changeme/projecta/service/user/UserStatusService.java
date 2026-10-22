package io.github.changeme.projecta.service.user;

import io.github.changeme.projecta.api.enums.UserStatus;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.service.user.entity.UserDO;
import io.github.changeme.projecta.service.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 每个请求都要确认"这个用户没被封禁"。JWT 本身无状态，签发后无法撤回，
 * 所以在拦截器里多查一次状态缓存（10 分钟），封禁时直接改写缓存，下一个请求立即生效。
 */
@Service
@RequiredArgsConstructor
public class UserStatusService {
    private static final String MISSING = "MISSING";

    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final AppProperties props;
    private final UserMapper userMapper;

    /** 用户不存在时返回 null。 */
    public UserStatus status(Long userId) {
        String key = keys.userStatus(userId);
        String cached = redis.opsForValue().get(key);
        if (cached == null) {
            UserDO user = userMapper.selectById(userId);
            cached = user == null ? MISSING : user.getStatus().name();
            redis.opsForValue().set(key, cached, props.getAuth().getUserStatusCacheTtl());
        }
        return MISSING.equals(cached) ? null : UserStatus.valueOf(cached);
    }

    public void put(Long userId, UserStatus status) {
        redis.opsForValue().set(keys.userStatus(userId), status.name(), props.getAuth().getUserStatusCacheTtl());
    }
}
