package io.github.changeme.projecta.service.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.changeme.projecta.api.enums.CacheMode;
import io.github.changeme.projecta.api.event.EventMessage;
import io.github.changeme.projecta.api.event.EventTypes;
import io.github.changeme.projecta.api.event.Payloads;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.config.RuntimeStrategy;
import io.github.changeme.projecta.core.mq.MqNames;
import io.github.changeme.projecta.core.redis.RedisKeys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 缓存（SPEC 9.4），三种模式由 RuntimeStrategy 决定：none / redis / two-level（Caffeine + Redis）。
 * <ul>
 *   <li>防穿透：不存在的数据也缓存一个空值（Redis 里是空串），保留 2 分钟；</li>
 *   <li>防击穿：普通 key 重建时加互斥锁（{@link #get}）；热点列表用逻辑过期 + 异步重建（{@link #getLogical}）；</li>
 *   <li>防雪崩：回填 Redis 时 TTL 加 0–5 分钟随机值，避免同一时刻大批 key 一起过期。</li>
 * </ul>
 * 写路径：事务提交后删 Redis，再广播 cache:invalidate 让所有实例清本地缓存；删 Redis 失败就发 MQ 重试补偿。
 */
@Slf4j
@Service
public class CacheService {
    private static final String NULL_MARKER = "";
    private static final Object LOCAL_NULL = new Object();
    private static final int LOCK_WAIT_ROUNDS = 20;
    private static final long LOCK_WAIT_MILLIS = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RuntimeStrategy runtime;
    private final AppProperties props;
    private final RedisKeys keys;
    private final RedisMessageListenerContainer listenerContainer;
    private final RabbitTemplate rabbitTemplate;
    private final MqNames mqNames;
    private final ThreadPoolTaskExecutor rebuildExecutor;
    private final Cache<String, Object> local;
    private final DefaultRedisScript<Long> unlockScript;
    /** 本机正在异步重建的 key，防止同一实例对同一个过期 key 提交多次重建任务。 */
    private final Set<String> rebuilding = ConcurrentHashMap.newKeySet();

    public CacheService(StringRedisTemplate redis, ObjectMapper objectMapper, RuntimeStrategy runtime,
                        AppProperties props, RedisKeys keys, RedisMessageListenerContainer listenerContainer,
                        RabbitTemplate rabbitTemplate, MqNames mqNames,
                        @Qualifier("cacheRebuildExecutor") ThreadPoolTaskExecutor rebuildExecutor) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.runtime = runtime;
        this.props = props;
        this.keys = keys;
        this.listenerContainer = listenerContainer;
        this.rabbitTemplate = rabbitTemplate;
        this.mqNames = mqNames;
        this.rebuildExecutor = rebuildExecutor;
        this.local = Caffeine.newBuilder()
                .maximumSize(props.getCache().getLocalMaxSize())
                .expireAfterWrite(props.getCache().getLocalTtl())
                .recordStats()
                .build();
        this.unlockScript = new DefaultRedisScript<>(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
                Long.class);
    }

    @PostConstruct
    void subscribe() {
        listenerContainer.addMessageListener((message, pattern) -> {
            String key = new String(message.getBody(), StandardCharsets.UTF_8);
            local.invalidate(key);
        }, new ChannelTopic(keys.channelCacheInvalidate()));
    }

    /** 读路径：本地缓存 -> Redis -> 数据库。loader 返回 null 表示数据不存在。 */
    public <T> Optional<T> get(String key, JavaType type, Duration ttl, Supplier<T> loader) {
        CacheMode mode = runtime.cacheMode();
        if (mode == CacheMode.NONE) {
            return Optional.ofNullable(loader.get());
        }
        if (mode == CacheMode.TWO_LEVEL) {
            Object cached = local.getIfPresent(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                T value = cached == LOCAL_NULL ? null : (T) cached;
                return Optional.ofNullable(value);
            }
        }
        T value = readRedisOrRebuild(key, type, ttl, loader);
        if (mode == CacheMode.TWO_LEVEL) {
            local.put(key, value == null ? LOCAL_NULL : value);
        }
        return Optional.ofNullable(value);
    }

    private <T> T readRedisOrRebuild(String key, JavaType type, Duration ttl, Supplier<T> loader) {
        for (int round = 0; round <= LOCK_WAIT_ROUNDS; round++) {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return NULL_MARKER.equals(json) ? null : fromJson(json, type);
            }
            // 未命中：抢重建锁，抢到的去查库并回填，没抢到的稍等再读缓存——保证同一时刻只有一个请求打到数据库
            String lockKey = key.replace(keys.prefix() + "cache:", keys.prefix() + "lock:cache:");
            String token = UUID.randomUUID().toString();
            Boolean locked = redis.opsForValue().setIfAbsent(lockKey, token, props.getCache().getRebuildLockTtl());
            if (Boolean.TRUE.equals(locked)) {
                try {
                    // 双重检查：等锁期间可能已经有人回填过了
                    String again = redis.opsForValue().get(key);
                    if (again != null) {
                        return NULL_MARKER.equals(again) ? null : fromJson(again, type);
                    }
                    T value = loader.get();
                    writeRedis(key, value, ttl);
                    return value;
                } finally {
                    redis.execute(unlockScript, List.of(lockKey), token);
                }
            }
            sleepQuietly();
        }
        // 等了约 1 秒仍没拿到结果（例如持锁者卡住），退回直接查库，宁可多一次查询也不让请求失败
        log.warn("cache rebuild wait timeout, load directly: {}", key);
        return loader.get();
    }

    private <T> void writeRedis(String key, T value, Duration ttl) {
        if (value == null) {
            redis.opsForValue().set(key, NULL_MARKER, props.getCache().getNullTtl());
            return;
        }
        long jitter = ThreadLocalRandom.current().nextLong(props.getCache().getArticleTtlJitter().toMillis() + 1);
        redis.opsForValue().set(key, toJson(value), ttl.plusMillis(jitter));
    }

    /**
     * 逻辑过期：Redis 里的值不设 TTL，而是在值里带一个过期时间。过期后由一个线程异步重建，
     * 其他请求继续返回旧值——热点 key 永远有值可读，不会出现大量请求同时打到数据库。
     * 代价是过期后的一小段时间里读到的是旧数据。
     */
    public <T> T getLogical(String key, JavaType type, Duration logicalTtl, Supplier<T> loader) {
        CacheMode mode = runtime.cacheMode();
        if (mode == CacheMode.NONE) {
            return loader.get();
        }
        if (mode == CacheMode.TWO_LEVEL) {
            Object cached = local.getIfPresent(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                T value = (T) cached;
                return value;
            }
        }
        JavaType wrapperType = objectMapper.getTypeFactory().constructParametricType(LogicalValue.class, type);
        String json = redis.opsForValue().get(key);
        T value;
        if (json == null) {
            // 第一次（或 Redis 被清空）只能同步构建
            value = loader.get();
            writeLogical(key, value, logicalTtl);
        } else {
            LogicalValue<T> wrapper = fromJson(json, wrapperType);
            value = wrapper.data();
            if (wrapper.expireAt().isBefore(LocalDateTime.now())) {
                rebuildAsync(key, logicalTtl, loader);
            }
        }
        if (mode == CacheMode.TWO_LEVEL && value != null) {
            local.put(key, value);
        }
        return value;
    }

    private <T> void rebuildAsync(String key, Duration logicalTtl, Supplier<T> loader) {
        if (!rebuilding.add(key)) {
            return;
        }
        String lockKey = keys.prefix() + "lock:logical:" + key;
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
        if (!Boolean.TRUE.equals(locked)) {
            rebuilding.remove(key);
            return;
        }
        rebuildExecutor.execute(() -> {
            try {
                writeLogical(key, loader.get(), logicalTtl);
                local.invalidate(key);
            } catch (Exception e) {
                log.warn("logical cache rebuild failed: {}", key, e);
            } finally {
                redis.delete(lockKey);
                rebuilding.remove(key);
            }
        });
    }

    private <T> void writeLogical(String key, T value, Duration logicalTtl) {
        redis.opsForValue().set(key, toJson(new LogicalValue<>(LocalDateTime.now().plus(logicalTtl), value)));
    }

    /** 写路径：在事务里调用时推迟到提交之后——提交前删缓存，其他请求可能把旧值又读回缓存。 */
    public void evict(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(key);
                }
            });
        } else {
            evictNow(key);
        }
    }

    public void evictNow(String key) {
        local.invalidate(key);
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("delete cache failed, send to retry queue: {}", key, e);
            sendDeleteRetry(key);
        }
        try {
            redis.convertAndSend(keys.channelCacheInvalidate(), key);
        } catch (Exception e) {
            // 广播失败时，其他实例的本地缓存最多 60 秒后自然过期
            log.warn("broadcast cache invalidate failed: {}", key, e);
        }
    }

    /** cache.retry.queue 的消费者调用；失败抛异常，交给 MQ 的重试队列。 */
    public void deleteForRetry(List<String> cacheKeys) {
        redis.delete(cacheKeys);
        cacheKeys.forEach(k -> redis.convertAndSend(keys.channelCacheInvalidate(), k));
    }

    public void invalidateLocal(String key) {
        local.invalidate(key);
    }

    public void clearLocal() {
        local.invalidateAll();
    }

    public long localSize() {
        return local.estimatedSize();
    }

    public boolean localContains(String key) {
        return local.getIfPresent(key) != null;
    }

    private void sendDeleteRetry(String key) {
        try {
            EventMessage event = new EventMessage(UUID.randomUUID().toString(), EventTypes.CACHE_DELETE,
                    LocalDateTime.now(), objectMapper.valueToTree(new Payloads.CacheDeleteEvent(List.of(key))));
            rabbitTemplate.send(mqNames.eventExchange(), EventTypes.CACHE_DELETE, MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(event))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.msgId())
                    .build());
        } catch (Exception e) {
            log.error("send cache delete retry failed, key={} may be stale until TTL", key, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T fromJson(String json, JavaType type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(LOCK_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 逻辑过期的包装：expireAt 之后视为过期，但值仍然可用。 */
    public record LogicalValue<T>(LocalDateTime expireAt, T data) {
    }
}
