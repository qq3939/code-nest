package io.github.qq3939.codenest.core.mq;

import io.github.qq3939.codenest.api.event.EventTypes;
import io.github.qq3939.codenest.core.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 交换机、队列、路由键的名字，和 SPEC 第 8 节一一对应。
 * 交换机带项目名前缀；队列名按 SPEC 原样使用。
 */
@Component
public class MqNames {
    public static final String NOTIFY_QUEUE = "notify.queue";
    public static final String FEED_QUEUE = "feed.queue";
    public static final String SEARCH_QUEUE = "search.queue";
    public static final String RANK_QUEUE = "rank.queue";
    public static final String CACHE_RETRY_QUEUE = "cache.retry.queue";

    public static final String HEADER_RETRY = "x-app-retry";
    public static final String HEADER_ERROR = "x-app-error";
    public static final String HEADER_ORIGIN_QUEUE = "x-app-origin-queue";

    /** 每个业务队列订阅哪些路由键。 */
    public static final Map<String, List<String>> BINDINGS = Map.of(
            NOTIFY_QUEUE, List.of(EventTypes.INTERACTION_LIKED, EventTypes.INTERACTION_COLLECTED,
                    EventTypes.COMMENT_CREATED, EventTypes.COMMENT_REPLIED, EventTypes.USER_FOLLOWED,
                    EventTypes.SYSTEM_NOTICE),
            FEED_QUEUE, List.of(EventTypes.ARTICLE_PUBLISHED, EventTypes.ARTICLE_REMOVED, EventTypes.USER_FOLLOWED),
            SEARCH_QUEUE, List.of(EventTypes.ARTICLE_PUBLISHED, EventTypes.ARTICLE_UPDATED, EventTypes.ARTICLE_REMOVED),
            RANK_QUEUE, List.of(EventTypes.ARTICLE_PUBLISHED, EventTypes.COMMENT_CREATED,
                    EventTypes.INTERACTION_LIKED, EventTypes.INTERACTION_COLLECTED),
            CACHE_RETRY_QUEUE, List.of(EventTypes.CACHE_DELETE));

    private final String eventExchange;
    private final String retryExchange;
    private final String dlxExchange;
    /** 各级重试的延迟（毫秒），默认 1 秒、5 秒、30 秒；最后一级重试仍失败就进死信。 */
    private final long[] retryDelaysMs;

    public MqNames(AppProperties props) {
        this.retryDelaysMs = props.getMq().getRetryDelays().stream().mapToLong(d -> d.toMillis()).toArray();
        this.eventExchange = props.getName() + ".event";
        this.retryExchange = props.getName() + ".retry";
        this.dlxExchange = props.getName() + ".dlx";
    }

    public String eventExchange() {
        return eventExchange;
    }

    public String retryExchange() {
        return retryExchange;
    }

    public String dlxExchange() {
        return dlxExchange;
    }

    public long[] retryDelaysMs() {
        return retryDelaysMs.clone();
    }

    public static String retryQueue(String queue, int attempt) {
        return queue + ".retry." + attempt;
    }

    public static String deadLetterQueue(String queue) {
        return queue + ".dlq";
    }
}
