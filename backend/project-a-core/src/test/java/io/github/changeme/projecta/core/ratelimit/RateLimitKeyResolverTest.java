package io.github.changeme.projecta.core.ratelimit;

import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.redis.RedisKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverTest {
    private final RedisKeys keys = new RedisKeys(new AppProperties());

    @Test
    void loggedInUserGetsUserAndIpKeys() {
        assertThat(RateLimitKeyResolver.resolve(keys, "like", new Dim[]{Dim.USER, Dim.IP}, 7L, "1.2.3.4"))
                .containsExactly("project-a:ratelimit:like:user:7", "project-a:ratelimit:like:ip:1.2.3.4");
    }

    @Test
    void anonymousUserFallsBackToIpAndDeduplicates() {
        assertThat(RateLimitKeyResolver.resolve(keys, "like", new Dim[]{Dim.USER, Dim.IP}, null, "1.2.3.4"))
                .containsExactly("project-a:ratelimit:like:ip:1.2.3.4");
    }

    @Test
    void ipOnly() {
        assertThat(RateLimitKeyResolver.resolve(keys, "search", new Dim[]{Dim.IP}, 7L, "::1"))
                .containsExactly("project-a:ratelimit:search:ip:::1");
    }
}
