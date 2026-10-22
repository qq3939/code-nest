package io.github.qq3939.codenest.core.ratelimit;

import io.github.qq3939.codenest.core.redis.RedisKeys;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 把"注解 + 当前请求"翻译成一组 Redis key。单独成类是为了能脱离 Spring 做单元测试。 */
public final class RateLimitKeyResolver {

    private RateLimitKeyResolver() {
    }

    /** 未登录用户按 USER 维度限流时退化成按 IP，否则匿名请求会全部挤在同一个 key 上。 */
    public static List<String> resolve(RedisKeys keys, String name, Dim[] dims, Long userId, String ip) {
        Set<String> result = new LinkedHashSet<>();
        for (Dim dim : dims) {
            if (dim == Dim.USER && userId != null) {
                result.add(keys.rateLimit(name, "user", String.valueOf(userId)));
            } else {
                result.add(keys.rateLimit(name, "ip", ip));
            }
        }
        return List.copyOf(result);
    }
}
