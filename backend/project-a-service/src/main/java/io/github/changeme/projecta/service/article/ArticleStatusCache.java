package io.github.changeme.projecta.service.article;

import io.github.changeme.projecta.api.enums.ArticleStatus;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.service.article.entity.ArticleDO;
import io.github.changeme.projecta.service.article.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章"状态 + 作者"的轻量缓存，给 Feed 读时过滤用：一页 20 条要判断哪些已删除、下架，
 * 用 MGET 一次取回，比查库快得多。值格式为 "STATUS:authorId"，已删除/不存在记为 "GONE:0"。
 */
@Component
@RequiredArgsConstructor
public class ArticleStatusCache {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String GONE = "GONE";

    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final ArticleMapper articleMapper;

    public record Brief(boolean visible, Long authorId) {
    }

    public Map<Long, Brief> get(List<Long> articleIds) {
        Map<Long, Brief> result = new HashMap<>();
        if (articleIds.isEmpty()) {
            return result;
        }
        List<String> values = redis.opsForValue().multiGet(articleIds.stream().map(keys::articleStatus).toList());
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < articleIds.size(); i++) {
            String v = values == null ? null : values.get(i);
            if (v == null) {
                missing.add(articleIds.get(i));
            } else {
                result.put(articleIds.get(i), parse(v));
            }
        }
        if (!missing.isEmpty()) {
            Map<Long, String> loaded = new HashMap<>();
            articleMapper.selectByIds(missing).forEach(a -> loaded.put(a.getId(), format(a)));
            missing.forEach(id -> loaded.putIfAbsent(id, GONE + ":0"));
            redis.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection conn = (StringRedisConnection) connection;
                loaded.forEach((id, v) -> conn.setEx(keys.articleStatus(id), TTL.toSeconds(), v));
                return null;
            });
            loaded.forEach((id, v) -> result.put(id, parse(v)));
        }
        return result;
    }

    public void evict(Long articleId) {
        redis.delete(keys.articleStatus(articleId));
    }

    private static String format(ArticleDO a) {
        return a.getStatus().name() + ":" + a.getAuthorId();
    }

    private static Brief parse(String v) {
        int idx = v.indexOf(':');
        String status = v.substring(0, idx);
        return new Brief(ArticleStatus.PUBLISHED.name().equals(status), Long.valueOf(v.substring(idx + 1)));
    }
}
