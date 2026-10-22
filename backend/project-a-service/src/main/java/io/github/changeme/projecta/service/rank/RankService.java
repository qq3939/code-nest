package io.github.changeme.projecta.service.rank;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.changeme.projecta.api.enums.RankPeriod;
import io.github.changeme.projecta.api.vo.ActiveRankVO;
import io.github.changeme.projecta.api.vo.ArticleCountVO;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.core.util.Times;
import io.github.changeme.projecta.service.article.ArticleRef;
import io.github.changeme.projecta.service.article.mapper.ArticleMapper;
import io.github.changeme.projecta.service.count.CountService;
import io.github.changeme.projecta.service.rank.entity.RankSnapshotDO;
import io.github.changeme.projecta.service.rank.mapper.RankSnapshotMapper;
import io.github.changeme.projecta.service.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排行（SPEC 9.9）：
 * <ul>
 *   <li>作者活跃日榜：rank.queue 消费事件后 ZINCRBY；周榜、月榜由最近 7/30 天的日榜 ZUNIONSTORE 合成，结果缓存 5 分钟。</li>
 *   <li>热门文章：每 10 分钟对最近 7 天的文章算一次热度分，取前 200 写进 ZSet。</li>
 *   <li>归档：每天 00:05 把昨天的榜单前 100 名写进 rank_snapshot。</li>
 * </ul>
 */
@Slf4j
@Service
public class RankService {
    public static final String TYPE_ACTIVE_DAY = "ACTIVE_DAY";
    public static final String TYPE_HOT_ARTICLE = "HOT_ARTICLE";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration DAY_KEEP = Duration.ofDays(35);
    private static final Duration DEDUP_KEEP = Duration.ofDays(1);
    private static final Duration UNION_CACHE = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final AppProperties props;
    private final ArticleMapper articleMapper;
    private final CountService countService;
    private final UserService userService;
    private final RankSnapshotMapper snapshotMapper;
    private final DefaultRedisScript<Long> addScript;

    public RankService(StringRedisTemplate redis, RedisKeys keys, AppProperties props, ArticleMapper articleMapper,
                       CountService countService, UserService userService, RankSnapshotMapper snapshotMapper) {
        this.redis = redis;
        this.keys = keys;
        this.props = props;
        this.articleMapper = articleMapper;
        this.countService = countService;
        this.userService = userService;
        this.snapshotMapper = snapshotMapper;
        this.addScript = new DefaultRedisScript<>();
        this.addScript.setLocation(new ClassPathResource("scripts/rank_add.lua"));
        this.addScript.setResultType(Long.class);
    }

    /** 返回 false 表示这条消息之前已经加过分（重复投递）。 */
    public boolean addActiveScore(String msgId, Long userId, int score) {
        String dayKey = keys.rankActive("day", LocalDate.now().format(DAY));
        Long added = redis.execute(addScript, List.of(keys.rankDedup(msgId), dayKey), String.valueOf(userId),
                String.valueOf(score), String.valueOf(DAY_KEEP.toSeconds()), String.valueOf(DEDUP_KEEP.toSeconds()));
        return added != null && added == 1;
    }

    public List<ActiveRankVO> activeRank(RankPeriod period, int limit) {
        String key = activeKey(period, LocalDate.now());
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = tuples.stream().map(t -> Long.valueOf(t.getValue())).toList();
        Map<Long, UserBriefVO> users = userService.briefs(userIds);
        List<ActiveRankVO> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            result.add(new ActiveRankVO(rank++, users.get(Long.valueOf(t.getValue())), t.getScore()));
        }
        return result;
    }

    /** 周榜/月榜 key 不存在（或已过期）时现场合成，5 分钟内的请求直接复用结果。 */
    String activeKey(RankPeriod period, LocalDate today) {
        if (period == RankPeriod.DAY) {
            return keys.rankActive("day", today.format(DAY));
        }
        int days = period == RankPeriod.WEEK ? 7 : 30;
        String name = period == RankPeriod.WEEK ? "week" : "month";
        String target = keys.rankActive(name, today.format(DAY));
        if (!Boolean.TRUE.equals(redis.hasKey(target))) {
            List<String> dayKeys = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                dayKeys.add(keys.rankActive("day", today.minusDays(i).format(DAY)));
            }
            redis.opsForZSet().unionAndStore(dayKeys.get(0), dayKeys.subList(1, dayKeys.size()), target);
            redis.expire(target, UNION_CACHE);
        }
        return target;
    }

    /** 重算热门文章。先写临时 key 再 RENAME，替换是原子的，读的人不会看到"算到一半"的榜单。 */
    public int refreshHotArticles() {
        LocalDateTime now = Times.now();
        List<ArticleRef> candidates = articleMapper.selectPublishedSince(now.minusDays(props.getRank().getHotWindowDays()));
        List<ScoredArticle> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += 500) {
            List<ArticleRef> chunk = candidates.subList(i, Math.min(candidates.size(), i + 500));
            Map<Long, ArticleCountVO> counts = countService.batchGet(chunk.stream().map(ArticleRef::id).toList());
            for (ArticleRef ref : chunk) {
                scored.add(new ScoredArticle(ref.id(), HotScore.compute(counts.get(ref.id()), ref.publishTime(), now)));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredArticle::score).reversed());
        List<ScoredArticle> top = scored.subList(0, Math.min(scored.size(), props.getRank().getHotTopN()));
        String target = keys.rankHotArticle();
        if (top.isEmpty()) {
            redis.delete(target);
            return 0;
        }
        String tmp = target + ":tmp";
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        // 分数可能非常小（新文章没有互动时为 0），统一加一个极小值保证排序稳定且大于 0
        top.forEach(s -> tuples.add(ZSetOperations.TypedTuple.of(String.valueOf(s.id()), s.score() + 1e-9)));
        redis.delete(tmp);
        redis.opsForZSet().add(tmp, tuples);
        redis.rename(tmp, target);
        return top.size();
    }

    public List<ScoredArticle> hotArticleIds(int limit) {
        if (!Boolean.TRUE.equals(redis.hasKey(keys.rankHotArticle()))) {
            refreshHotArticles();
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet()
                .reverseRangeWithScores(keys.rankHotArticle(), 0, limit - 1);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream().map(t -> new ScoredArticle(Long.valueOf(t.getValue()), t.getScore())).toList();
    }

    public void archive(LocalDate date) {
        String day = date.format(DAY);
        int n = props.getRank().getArchiveTopN();
        save(TYPE_ACTIVE_DAY, day, redis.opsForZSet().reverseRangeWithScores(keys.rankActive("day", day), 0, n - 1));
        save(TYPE_HOT_ARTICLE, day, redis.opsForZSet().reverseRangeWithScores(keys.rankHotArticle(), 0, n - 1));
    }

    private void save(String type, String period, Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null) {
            return;
        }
        // 先删后写，任务重跑时不会产生重复的归档记录
        snapshotMapper.delete(Wrappers.<RankSnapshotDO>lambdaQuery()
                .eq(RankSnapshotDO::getRankType, type).eq(RankSnapshotDO::getPeriod, period));
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            RankSnapshotDO row = new RankSnapshotDO();
            row.setRankType(type);
            row.setPeriod(period);
            row.setRankNo(rank++);
            row.setSubjectId(Long.valueOf(t.getValue()));
            row.setScore(BigDecimal.valueOf(t.getScore()).setScale(4, RoundingMode.HALF_UP));
            snapshotMapper.insert(row);
        }
        log.info("rank {} of {} archived, {} rows", type, period, rank - 1);
    }

    public record ScoredArticle(Long id, double score) {
    }
}
