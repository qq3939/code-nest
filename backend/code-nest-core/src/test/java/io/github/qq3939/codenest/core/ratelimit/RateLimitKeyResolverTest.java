package io.github.qq3939.codenest.core.ratelimit;

import io.github.qq3939.codenest.core.config.AppProperties;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverTest {
    private final RedisKeys keys = new RedisKeys(new AppProperties());

    @Test
    void loggedInUserGetsUserAndIpKeys() {
        assertThat(RateLimitKeyResolver.resolve(keys, "like", new Dim[]{Dim.USER, Dim.IP}, 7L, "1.2.3.4"))
                .containsExactly("code-nest:ratelimit:like:user:7", "code-nest:ratelimit:like:ip:1.2.3.4");
    }

    @Test
    void anonymousUserFallsBackToIpAndDeduplicates() {
        assertThat(RateLimitKeyResolver.resolve(keys, "like", new Dim[]{Dim.USER, Dim.IP}, null, "1.2.3.4"))
                .containsExactly("code-nest:ratelimit:like:ip:1.2.3.4");
    }

    @Test
    void ipOnly() {
        assertThat(RateLimitKeyResolver.resolve(keys, "search", new Dim[]{Dim.IP}, 7L, "::1"))
                .containsExactly("code-nest:ratelimit:search:ip:::1");
    }
}
