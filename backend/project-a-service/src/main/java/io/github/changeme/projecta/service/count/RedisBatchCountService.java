package io.github.changeme.projecta.service.count;

import io.github.changeme.projecta.api.enums.CountStrategy;
import io.github.changeme.projecta.api.vo.ArticleCountVO;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.service.count.entity.ArticleStatDO;
import io.github.changeme.projecta.service.count.mapper.ArticleStatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 默认策略：计数先加在 Redis Hash 上，同时记下"脏"文章 ID；定时任务每 10 秒把脏文章的绝对值批量写回 MySQL。
 * 好处：高并发阅读时数据库写次数从"每次请求一次"降到"每 10 秒每篇一次"。
 * 代价：Redis 宕机且未持久化时，最多丢失最近一个写回周期 + AOF everysec 的 1 秒数据（见 D-007）。
 */
@Slf4j
@Component
public class RedisBatchCountService implements CountService {
    private static final int MAX_ROUNDS_PER_FLUSH = 20;

    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final ArticleStatMapper mapper;
    private final AppProperties props;
    private final DefaultRedisScript<Long> incrScript;
    private final DefaultRedisScript<Long> initScript;

    public RedisBatchCountService(StringRedisTemplate redis, RedisKeys keys, ArticleStatMapper mapper,
                                  AppProperties props) {
        this.redis = redis;
        this.keys = keys;
        this.mapper = mapper;
        this.props = props;
        this.incrScript = script("scripts/count_incr.lua");
        this.initScript = script("scripts/count_init.lua");
    }

    @Override
    public CountStrategy strategy() {
        return CountStrategy.REDIS_BATCH;
    }

    @Override
    public void increase(Long articleId, CountField field, long delta) {
        List<String> scriptKeys = List.of(keys.articleCount(articleId), keys.articleCountDirty());
        Long result = redis.execute(incrScript, scriptKeys, field.key(), String.valueOf(delta), String.valueOf(articleId));
        if (result != null && result == -1) {
            loadFromDb(articleId);
            redis.execute(incrScript, scriptKeys, field.key(), String.valueOf(delta), String.valueOf(articleId));
        }
    }

    @Override
    public ArticleCountVO get(Long articleId) {
        return batchGet(List.of(articleId)).get(articleId);
    }

    /** 用 pipeline 一次往返取回多篇文章的 Hash；未命中的再从库里批量加载。 */
    @Override
    public Map<Long, ArticleCountVO> batchGet(Collection<Long> articleIds) {
        List<Long> ids = new ArrayList<>(articleIds);
        Map<Long, ArticleCountVO> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<Object> hashes = redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            ids.forEach(id -> conn.hGetAll(keys.articleCount(id)));
            return null;
        });
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> hash = (Map<Object, Object>) hashes.get(i);
            if (hash == null || hash.isEmpty()) {
                missing.add(ids.get(i));
            } else {
                result.put(ids.get(i), CountConverter.fromHash(hash));
            }
        }
        if (!missing.isEmpty()) {
            Map<Long, ArticleStatDO> rows = mapper.selectByIds(missing).stream()
                    .collect(Collectors.toMap(ArticleStatDO::getArticleId, Function.identity()));
            for (Long id : missing) {
                ArticleCountVO vo = CountConverter.toVO(rows.get(id));
                init(id, vo);
                result.put(id, vo);
            }
        }
        return result;
    }

    /** 写回：SPOP 一批脏 ID，读出 Redis 里的绝对值，批量 upsert；写库失败就把 ID 放回脏集合。 */
    public int flush() {
        int total = 0;
        int batch = props.getCount().getFlushBatchSize();
        for (int round = 0; round < MAX_ROUNDS_PER_FLUSH; round++) {
            List<String> popped = redis.opsForSet().pop(keys.articleCountDirty(), batch);
            if (popped == null || popped.isEmpty()) {
                break;
            }
            List<Long> ids = popped.stream().map(Long::valueOf).toList();
            try {
                List<ArticleStatDO> rows = new ArrayList<>();
                readHashes(ids).forEach((id, vo) -> rows.add(CountConverter.toDO(id, vo)));
                if (!rows.isEmpty()) {
                    mapper.upsertBatch(rows);
                }
                total += rows.size();
            } catch (Exception e) {
                redis.opsForSet().add(keys.articleCountDirty(), popped.toArray(String[]::new));
                log.error("count flush failed, {} ids put back", popped.size(), e);
                break;
            }
            if (popped.size() < batch) {
                break;
            }
        }
        if (total > 0) {
            log.debug("count flushed {} articles", total);
        }
        return total;
    }

    /**
     * 对账：点赞、收藏、评论数以关系表为准重新统计；阅读数没有明细表可依，取 Redis 和库里较大的一个。
     * 只修正有差异的文章，并同步到 Redis Hash（若存在）。
     */
    public int reconcile(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return 0;
        }
        Map<Long, Long> likes = toMap(mapper.countArticleLikes(articleIds));
        Map<Long, Long> collects = toMap(mapper.countCollects(articleIds));
        Map<Long, Long> comments = toMap(mapper.countComments(articleIds));
        Map<Long, ArticleStatDO> dbRows = mapper.selectByIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleStatDO::getArticleId, Function.identity()));
        Map<Long, ArticleCountVO> cached = readHashes(articleIds);
        List<ArticleStatDO> fixes = new ArrayList<>();
        for (Long id : articleIds) {
            ArticleCountVO db = CountConverter.toVO(dbRows.get(id));
            ArticleCountVO inRedis = cached.get(id);
            long read = Math.max(db.read(), inRedis == null ? 0 : inRedis.read());
            ArticleCountVO truth = new ArticleCountVO(read, likes.getOrDefault(id, 0L),
                    collects.getOrDefault(id, 0L), comments.getOrDefault(id, 0L));
            if (!truth.equals(db) || (inRedis != null && !truth.equals(inRedis))) {
                fixes.add(CountConverter.toDO(id, truth));
                if (inRedis != null) {
                    redis.opsForHash().putAll(keys.articleCount(id), Map.of("read", String.valueOf(truth.read()),
                            "like", String.valueOf(truth.like()), "collect", String.valueOf(truth.collect()),
                            "comment", String.valueOf(truth.comment())));
                }
            }
        }
        if (!fixes.isEmpty()) {
            mapper.upsertBatch(fixes);
            log.info("reconcile fixed {} articles", fixes.size());
        }
        return fixes.size();
    }

    /** 从 redis-batch 切走时调用：先把脏数据全部写回，再清掉所有计数 Hash，防止切回来时读到过期值。 */
    public void flushAndClear() {
        while (Boolean.TRUE.equals(redis.hasKey(keys.articleCountDirty()))) {
            if (flush() == 0) {
                break;
            }
        }
        List<String> found = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(keys.prefix() + "count:article:*").count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(found::add);
        }
        if (!found.isEmpty()) {
            redis.delete(found);
        }
    }

    private Map<Long, ArticleCountVO> readHashes(List<Long> ids) {
        List<Object> hashes = redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            ids.forEach(id -> conn.hGetAll(keys.articleCount(id)));
            return null;
        });
        Map<Long, ArticleCountVO> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> hash = (Map<Object, Object>) hashes.get(i);
            if (hash != null && !hash.isEmpty()) {
                result.put(ids.get(i), CountConverter.fromHash(hash));
            }
        }
        return result;
    }

    private void loadFromDb(Long articleId) {
        init(articleId, CountConverter.toVO(mapper.selectById(articleId)));
    }

    private void init(Long articleId, ArticleCountVO vo) {
        redis.execute(initScript, List.of(keys.articleCount(articleId)), String.valueOf(vo.read()),
                String.valueOf(vo.like()), String.valueOf(vo.collect()), String.valueOf(vo.comment()));
    }

    private static Map<Long, Long> toMap(List<IdCount> rows) {
        return rows.stream().collect(Collectors.toMap(IdCount::id, IdCount::cnt));
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
