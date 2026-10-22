package io.github.qq3939.codenest.service.admin;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.dto.StrategyUpdateReq;
import io.github.qq3939.codenest.api.enums.CacheMode;
import io.github.qq3939.codenest.api.enums.CountStrategy;
import io.github.qq3939.codenest.api.enums.FeedMode;
import io.github.qq3939.codenest.api.vo.StrategyVO;
import io.github.qq3939.codenest.core.config.AppProperties;
import io.github.qq3939.codenest.core.config.RuntimeStrategy;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.service.cache.CacheService;
import io.github.qq3939.codenest.service.count.RoutingCountService;
import io.github.qq3939.codenest.service.feed.FeedService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 实验开关（SPEC 9.14）：运行时切换计数、Feed、缓存策略，变更经 strategy:refresh 广播到所有实例。
 * 只在 app.dev-tools.enabled=true 时可用，Controller 层会再检查一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyService {
    private final RuntimeStrategy runtime;
    private final AppProperties props;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final RedisMessageListenerContainer listenerContainer;
    private final RoutingCountService countService;
    private final FeedService feedService;
    private final CacheService cacheService;

    @PostConstruct
    void subscribe() {
        listenerContainer.addMessageListener((message, pattern) -> apply(new String(message.getBody(),
                StandardCharsets.UTF_8)), new ChannelTopic(keys.channelStrategyRefresh()));
    }

    public StrategyVO current() {
        return new StrategyVO(runtime.countStrategy().value(), runtime.feedMode().value(), runtime.cacheMode().value());
    }

    public StrategyVO update(StrategyUpdateReq req) {
        if (!props.getDevTools().isEnabled()) {
            throw new BizException(ErrorCode.FEATURE_DISABLED);
        }
        CountStrategy count = parse(req.countStrategy(), CountStrategy::fromValue);
        FeedMode feed = parse(req.feedMode(), FeedMode::fromValue);
        CacheMode cache = parse(req.cacheMode(), CacheMode::fromValue);

        // 收尾工作只在发起切换的实例上做一次，不需要每个实例都做
        if (count != null && count != runtime.countStrategy()) {
            countService.beforeSwitch(runtime.countStrategy(), count);
        }
        boolean feedChanged = feed != null && feed != runtime.feedMode();
        StrategyVO target = new StrategyVO(
                (count != null ? count : runtime.countStrategy()).value(),
                (feed != null ? feed : runtime.feedMode()).value(),
                (cache != null ? cache : runtime.cacheMode()).value());
        String message = target.countStrategy() + "|" + target.feedMode() + "|" + target.cacheMode();
        apply(message);
        redis.convertAndSend(keys.channelStrategyRefresh(), message);
        if (feedChanged) {
            int cleared = feedService.clearInboxes();
            log.info("feed mode switched to {}, {} inboxes cleared", target.feedMode(), cleared);
        }
        return current();
    }

    private void apply(String message) {
        String[] parts = message.split("\\|");
        CacheMode before = runtime.cacheMode();
        runtime.update(CountStrategy.fromValue(parts[0]), FeedMode.fromValue(parts[1]), CacheMode.fromValue(parts[2]));
        if (before != runtime.cacheMode()) {
            // 换缓存模式时清空本地缓存，避免 two-level 切回来时读到切换期间的旧值
            cacheService.clearLocal();
        }
        log.info("strategy applied: {}", message);
    }

    private static <T> T parse(String value, java.util.function.Function<String, T> parser) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.STRATEGY_INVALID);
        }
    }
}
