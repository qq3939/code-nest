package io.github.qq3939.codenest.core.config;

import io.github.qq3939.codenest.api.enums.CacheMode;
import io.github.qq3939.codenest.api.enums.CountStrategy;
import io.github.qq3939.codenest.api.enums.FeedMode;
import org.springframework.stereotype.Component;

/**
 * 当前生效的三个策略。启动时取配置文件的值，运行时可以被实验开关改写（各实例通过 strategy:refresh 广播同步）。
 * 用 volatile 而不是锁：读远多于写，且三个字段之间没有必须同时变化的约束。
 */
@Component
public class RuntimeStrategy {
    private volatile CountStrategy countStrategy;
    private volatile FeedMode feedMode;
    private volatile CacheMode cacheMode;

    public RuntimeStrategy(AppProperties props) {
        this.countStrategy = props.getCount().getStrategy();
        this.feedMode = props.getFeed().getMode();
        this.cacheMode = props.getCache().getMode();
    }

    public CountStrategy countStrategy() {
        return countStrategy;
    }

    public FeedMode feedMode() {
        return feedMode;
    }

    public CacheMode cacheMode() {
        return cacheMode;
    }

    public void update(CountStrategy count, FeedMode feed, CacheMode cache) {
        if (count != null) {
            this.countStrategy = count;
        }
        if (feed != null) {
            this.feedMode = feed;
        }
        if (cache != null) {
            this.cacheMode = cache;
        }
    }
}
