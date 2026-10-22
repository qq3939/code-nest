package io.github.qq3939.codenest.web;

import com.fasterxml.jackson.databind.JavaType;
import io.github.qq3939.codenest.core.config.AppProperties;
import io.github.qq3939.codenest.core.config.RuntimeStrategy;
import io.github.qq3939.codenest.core.mq.MqNames;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.service.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 两级缓存（SPEC 9.4 验收）：更新后立即可见、空值缓存防穿透、本地缓存跨实例失效广播。 */
class CacheIT extends IntegrationTestBase {

    @Autowired
    CacheService cacheService;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    RuntimeStrategy runtime;
    @Autowired
    AppProperties props;
    @Autowired
    RedisKeys keys;
    @Autowired
    RedisMessageListenerContainer listenerContainer;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    MqNames mqNames;
    @Autowired
    @Qualifier("cacheRebuildExecutor")
    ThreadPoolTaskExecutor rebuildExecutor;

    @Test
    void updateIsVisibleImmediately() {
        TestUser author = newUser();
        long id = publishArticle(author, "旧标题");
        assertThat(ok(call(HttpMethod.GET, "/api/articles/" + id, null, null)).path("title").asText()).isEqualTo("旧标题");

        ok(call(HttpMethod.PUT, "/api/articles/" + id, author, articleBody("新标题", "修改后的正文内容，足够长。")));
        assertThat(ok(call(HttpMethod.GET, "/api/articles/" + id, null, null)).path("title").asText()).isEqualTo("新标题");
    }

    @Test
    void missingValueIsCachedAsNull() {
        String key = keys.prefix() + "it:cache:null:" + UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        JavaType type = objectMapper.constructType(String.class);
        for (int i = 0; i < 5; i++) {
            Optional<String> v = cacheService.get(key, type, Duration.ofMinutes(1), () -> {
                loads.incrementAndGet();
                return null;
            });
            assertThat(v).isEmpty();
        }
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void evictIsBroadcastToOtherInstances() {
        // 第二个 CacheService 模拟另一台应用实例：独立的本地缓存，共享同一个 Redis
        CacheService other = new CacheService(redis, objectMapper, runtime, props, keys, listenerContainer,
                rabbitTemplate, mqNames, rebuildExecutor);
        ReflectionTestUtils.invokeMethod(other, "subscribe");

        String key = keys.prefix() + "it:cache:bcast:" + UUID.randomUUID();
        JavaType type = objectMapper.constructType(Map.class);
        other.get(key, type, Duration.ofMinutes(1), () -> Map.of("v", 1));
        assertThat(other.localContains(key)).isTrue();

        cacheService.evictNow(key);
        await().atMost(Duration.ofSeconds(5)).until(() -> !other.localContains(key));
        assertThat(redis.hasKey(key)).isFalse();
    }
}
