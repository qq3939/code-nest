package io.github.changeme.projecta.service.count;

import io.github.changeme.projecta.api.enums.CountStrategy;
import io.github.changeme.projecta.api.vo.ArticleCountVO;
import io.github.changeme.projecta.core.config.RuntimeStrategy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 业务代码注入的 CountService 就是它：按 RuntimeStrategy 的当前值，把调用转给对应的实现。
 * 这是"策略模式"：新增一种计数方式只需要再写一个实现类。
 */
@Primary
@Service
public class RoutingCountService implements CountService {
    private final Map<CountStrategy, CountService> delegates = new EnumMap<>(CountStrategy.class);
    private final RuntimeStrategy runtime;
    private final RedisBatchCountService redisBatch;

    public RoutingCountService(List<CountService> all, RuntimeStrategy runtime, RedisBatchCountService redisBatch) {
        all.stream().filter(s -> !(s instanceof RoutingCountService)).forEach(s -> delegates.put(s.strategy(), s));
        this.runtime = runtime;
        this.redisBatch = redisBatch;
    }

    @Override
    public CountStrategy strategy() {
        return runtime.countStrategy();
    }

    @Override
    public void increase(Long articleId, CountField field, long delta) {
        current().increase(articleId, field, delta);
    }

    @Override
    public ArticleCountVO get(Long articleId) {
        return current().get(articleId);
    }

    @Override
    public Map<Long, ArticleCountVO> batchGet(Collection<Long> articleIds) {
        return current().batchGet(articleIds);
    }

    /** 策略切换前的收尾：离开 redis-batch 时先把 Redis 里的计数写回并清空。 */
    public void beforeSwitch(CountStrategy from, CountStrategy to) {
        if (from == CountStrategy.REDIS_BATCH && to != CountStrategy.REDIS_BATCH) {
            redisBatch.flushAndClear();
        }
    }

    private CountService current() {
        return delegates.get(runtime.countStrategy());
    }
}
