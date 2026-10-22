package io.github.qq3939.codenest.service.feed;

import io.github.qq3939.codenest.api.common.CursorPage;
import io.github.qq3939.codenest.api.enums.ArticleStatus;
import io.github.qq3939.codenest.api.enums.FeedMode;
import io.github.qq3939.codenest.api.vo.ArticleListItemVO;
import io.github.qq3939.codenest.core.config.AppProperties;
import io.github.qq3939.codenest.core.config.RuntimeStrategy;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.core.util.Times;
import io.github.qq3939.codenest.service.article.ArticleAssembler;
import io.github.qq3939.codenest.service.article.ArticleRef;
import io.github.qq3939.codenest.service.article.ArticleStatusCache;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.article.mapper.ArticleMapper;
import io.github.qq3939.codenest.service.follow.FollowService;
import io.github.qq3939.codenest.service.follow.entity.FollowDO;
import io.github.qq3939.codenest.service.user.UserStatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关注动态 Feed（SPEC 9.3），三种模式：
 * <ul>
 *   <li>push（推）：发文时写进每个粉丝的收件箱，读只读自己的收件箱。读快，大 V 发文写放大严重。</li>
 *   <li>pull（拉）：每个作者只写自己的发件箱，读时合并所有关注者的发件箱。写快，关注多的人读得慢。</li>
 *   <li>hybrid（默认）：普通作者推，大 V 只写发件箱；读时合并"自己的收件箱 + 关注的大 V 的发件箱"。</li>
 * </ul>
 * 发件箱无论哪种模式都写（一次 ZADD，很便宜），这样运行时切换模式不需要补数据。
 */
@Slf4j
@Service
public class FeedService {
    /** 空收件箱/发件箱的占位成员，分数 0；读取时分数下界取 1，把它排除在外。 */
    private static final String SENTINEL = "0";

    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final AppProperties props;
    private final RuntimeStrategy runtime;
    private final ArticleMapper articleMapper;
    private final ArticleAssembler assembler;
    private final ArticleStatusCache statusCache;
    private final FollowService followService;
    private final UserStatService userStatService;
    private final String pushScript;

    public FeedService(StringRedisTemplate redis, RedisKeys keys, AppProperties props, RuntimeStrategy runtime,
                       ArticleMapper articleMapper, ArticleAssembler assembler, ArticleStatusCache statusCache,
                       FollowService followService, UserStatService userStatService) throws IOException {
        this.redis = redis;
        this.keys = keys;
        this.props = props;
        this.runtime = runtime;
        this.articleMapper = articleMapper;
        this.assembler = assembler;
        this.statusCache = statusCache;
        this.followService = followService;
        this.userStatService = userStatService;
        this.pushScript = StreamUtils.copyToString(new ClassPathResource("scripts/feed_push.lua").getInputStream(),
                StandardCharsets.UTF_8);
    }

    // ======================= 读 =======================

    public CursorPage<ArticleListItemVO> read(Long userId, String cursorText, int size) {
        FeedCursor cursor = FeedCursor.decode(cursorText);
        List<String> sources = sourceKeys(userId);
        FeedMerger.Result result = sources.size() == 1
                ? readSingle(sources.get(0), cursor, size)
                : readMerged(sources, cursor, size);
        redis.expire(keys.feedInbox(userId), props.getFeed().getInboxTtl());

        List<ArticleListItemVO> items = filterAndAssemble(userId, result.page());
        return new CursorPage<>(items, result.hasMore() ? result.next().encode() : null, result.hasMore());
    }

    /** 当前模式下，这个用户的 Feed 由哪些 ZSet 组成。 */
    List<String> sourceKeys(Long userId) {
        FeedMode mode = runtime.feedMode();
        List<String> sources = new ArrayList<>();
        if (mode == FeedMode.PULL) {
            List<Long> followings = followService.followingIds(userId);
            ensureOutboxes(followings);
            followings.forEach(id -> sources.add(keys.feedOutbox(id)));
            return sources.isEmpty() ? List.of(keys.feedInbox(userId)) : sources;
        }
        ensureInbox(userId, mode);
        sources.add(keys.feedInbox(userId));
        if (mode == FeedMode.HYBRID) {
            List<Long> bigVs = followedBigV(userId);
            ensureOutboxes(bigVs);
            bigVs.forEach(id -> sources.add(keys.feedOutbox(id)));
        }
        return sources;
    }

    /** 单一来源：直接用 ZREVRANGEBYSCORE key max 1 LIMIT offset size+1，不需要归并。 */
    private FeedMerger.Result readSingle(String key, FeedCursor cursor, int size) {
        List<FeedMerger.Entry> entries = range(key, cursor.maxScore(), cursor.offset(), size + 1);
        boolean hasMore = entries.size() > size;
        List<FeedMerger.Entry> page = hasMore ? entries.subList(0, size) : entries;
        return new FeedMerger.Result(List.copyOf(page), FeedMerger.nextCursor(page, cursor), hasMore);
    }

    /** 多个来源：每个来源取 size + offset + 1 条（用 pipeline 一次往返），交给 FeedMerger 归并。 */
    private FeedMerger.Result readMerged(List<String> sourceKeys, FeedCursor cursor, int size) {
        int fetch = size + cursor.offset() + 1;
        List<Object> raw = redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            for (String key : sourceKeys) {
                // 下界取 1 而不是 0：把分数为 0 的占位成员排除在外
                conn.zRevRangeByScoreWithScores(key, 1, cursor.maxScore(), 0, fetch);
            }
            return null;
        });
        List<List<FeedMerger.Entry>> sources = new ArrayList<>();
        for (Object o : raw) {
            @SuppressWarnings("unchecked")
            Set<ZSetOperations.TypedTuple<String>> tuples = (Set<ZSetOperations.TypedTuple<String>>) o;
            sources.add(toEntries(tuples));
        }
        return FeedMerger.merge(sources, cursor, size);
    }

    private List<FeedMerger.Entry> range(String key, long max, int offset, int count) {
        return toEntries(redis.opsForZSet().reverseRangeByScoreWithScores(key, 1, max, offset, count));
    }

    private static List<FeedMerger.Entry> toEntries(Set<ZSetOperations.TypedTuple<String>> tuples) {
        List<FeedMerger.Entry> list = new ArrayList<>();
        if (tuples == null) {
            return list;
        }
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            if (t.getValue() == null || SENTINEL.equals(t.getValue()) || t.getScore() == null || t.getScore() <= 0) {
                continue;
            }
            list.add(new FeedMerger.Entry(Long.parseLong(t.getValue()), t.getScore().longValue()));
        }
        return list;
    }

    /** 读时过滤：已删除、已下架、已取关作者的文章不返回（状态查询走 ArticleStatusCache）。 */
    private List<ArticleListItemVO> filterAndAssemble(Long userId, List<FeedMerger.Entry> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> ids = page.stream().map(FeedMerger.Entry::articleId).toList();
        Map<Long, ArticleStatusCache.Brief> briefs = statusCache.get(ids);
        Set<Long> authors = briefs.values().stream().map(ArticleStatusCache.Brief::authorId).collect(Collectors.toSet());
        Set<Long> followed = followService.followedAmong(userId, authors);
        List<Long> visible = ids.stream().filter(id -> {
            ArticleStatusCache.Brief b = briefs.get(id);
            return b != null && b.visible() && followed.contains(b.authorId());
        }).toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleDO> rows = articleMapper.selectByIds(visible).stream()
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
                .collect(Collectors.toMap(ArticleDO::getId, Function.identity()));
        List<ArticleDO> ordered = visible.stream().map(rows::get).filter(a -> a != null).toList();
        return assembler.toListItems(ordered);
    }

    // ======================= 收件箱 / 发件箱维护 =======================

    /** 冷启动：收件箱不存在时，从库里取关注的人最近 500 篇（hybrid 下排除大 V，他们的文章从发件箱读）。 */
    void ensureInbox(Long userId, FeedMode mode) {
        String key = keys.feedInbox(userId);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        List<Long> exclude = mode == FeedMode.HYBRID ? followedBigV(userId) : List.of();
        List<ArticleRef> seed = articleMapper.selectFeedSeed(userId, exclude, props.getFeed().getInboxMaxSize());
        writeZSet(key, seed);
        redis.expire(key, props.getFeed().getInboxTtl());
    }

    void ensureOutboxes(List<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return;
        }
        List<Object> exists = redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            authorIds.forEach(id -> conn.exists(keys.feedOutbox(id)));
            return null;
        });
        for (int i = 0; i < authorIds.size(); i++) {
            if (!Boolean.TRUE.equals(exists.get(i))) {
                Long authorId = authorIds.get(i);
                writeZSet(keys.feedOutbox(authorId),
                        articleMapper.selectRecentByAuthor(authorId, props.getFeed().getOutboxMaxSize()));
            }
        }
    }

    private void writeZSet(String key, List<ArticleRef> refs) {
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        tuples.add(ZSetOperations.TypedTuple.of(SENTINEL, 0D));
        refs.forEach(r -> tuples.add(ZSetOperations.TypedTuple.of(String.valueOf(r.id()),
                (double) Times.toMillis(r.publishTime()))));
        redis.opsForZSet().add(key, tuples);
    }

    /** 该用户关注的大 V，缓存 10 分钟；关注/取关时删除。 */
    List<Long> followedBigV(Long userId) {
        String key = keys.feedBigVFollow(userId);
        Set<String> cached = redis.opsForSet().members(key);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().filter(s -> !SENTINEL.equals(s)).map(Long::valueOf).toList();
        }
        List<Long> bigVs = followService.followedBigV(userId, props.getFeed().getBigVThreshold());
        List<String> members = new ArrayList<>(bigVs.stream().map(String::valueOf).toList());
        members.add(SENTINEL);
        redis.opsForSet().add(key, members.toArray(String[]::new));
        redis.expire(key, props.getFeed().getBigVFollowTtl());
        return bigVs;
    }

    public boolean isBigV(Long authorId) {
        return userStatService.followerCount(authorId) >= props.getFeed().getBigVThreshold();
    }

    // ======================= 写（由 feed.queue 消费者调用） =======================

    /** 发文：写发件箱；push 模式、或 hybrid 下的普通作者，再分批扇出到粉丝收件箱。返回扇出的粉丝数。 */
    public int onPublished(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null || article.getStatus() != ArticleStatus.PUBLISHED) {
            // 消息到达时文章已经被删除或下架（消息乱序），以数据库当前状态为准
            return 0;
        }
        long score = Times.toMillis(article.getPublishTime());
        String outbox = keys.feedOutbox(article.getAuthorId());
        ensureOutboxes(List.of(article.getAuthorId()));
        redis.opsForZSet().add(outbox, String.valueOf(articleId), score);
        redis.opsForZSet().removeRange(outbox, 0, -(props.getFeed().getOutboxMaxSize() + 2L));

        FeedMode mode = runtime.feedMode();
        if (mode == FeedMode.PULL || (mode == FeedMode.HYBRID && isBigV(article.getAuthorId()))) {
            return 0;
        }
        return fanout(article.getAuthorId(), articleId, score, true);
    }

    public void onRemoved(Long articleId, Long authorId) {
        redis.opsForZSet().remove(keys.feedOutbox(authorId), String.valueOf(articleId));
        fanout(authorId, articleId, 0, false);
    }

    /** 关注时回填对方最近 20 篇；取关时从收件箱移除对方的文章。都以数据库当前关注状态为准，防止消息乱序。 */
    public void onFollowChanged(Long followerId, Long followeeId) {
        redis.delete(keys.feedBigVFollow(followerId));
        String inbox = keys.feedInbox(followerId);
        boolean following = followService.isFollowing(followerId, followeeId);
        if (!Boolean.TRUE.equals(redis.hasKey(inbox))) {
            return;
        }
        FeedMode mode = runtime.feedMode();
        if (following) {
            if (mode == FeedMode.PULL || (mode == FeedMode.HYBRID && isBigV(followeeId))) {
                return;
            }
            List<ArticleRef> recent = articleMapper.selectRecentByAuthor(followeeId, props.getFeed().getBackfillSize());
            for (ArticleRef r : recent) {
                redis.opsForZSet().add(inbox, String.valueOf(r.id()), Times.toMillis(r.publishTime()));
            }
            redis.opsForZSet().removeRange(inbox, 0, -(props.getFeed().getInboxMaxSize() + 2L));
        } else {
            List<ArticleRef> recent = articleMapper.selectRecentByAuthor(followeeId, props.getFeed().getInboxMaxSize());
            if (!recent.isEmpty()) {
                redis.opsForZSet().remove(inbox, recent.stream().map(r -> String.valueOf(r.id())).toArray());
            }
        }
    }

    /** 分批读粉丝（每批 500），每批用一次 pipeline 把文章推进各自的收件箱（或从中移除）。 */
    private int fanout(Long authorId, Long articleId, long score, boolean add) {
        int batchSize = props.getFeed().getFanoutBatchSize();
        int max = props.getFeed().getInboxMaxSize();
        long afterId = 0;
        int total = 0;
        while (true) {
            List<FollowDO> batch = followService.followersAfter(authorId, afterId, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            redis.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection conn = (StringRedisConnection) connection;
                for (FollowDO f : batch) {
                    String inbox = keys.feedInbox(f.getFollowerId());
                    if (add) {
                        conn.eval(pushScript, ReturnType.INTEGER, 1, inbox, String.valueOf(articleId),
                                String.valueOf(score), String.valueOf(max));
                    } else {
                        conn.zRem(inbox, String.valueOf(articleId));
                    }
                }
                return null;
            });
            total += batch.size();
            afterId = batch.get(batch.size() - 1).getId();
            if (batch.size() < batchSize) {
                break;
            }
        }
        log.debug("fanout article {} of author {} to {} followers, add={}", articleId, authorId, total, add);
        return total;
    }

    /** 切换 Feed 模式后清空所有收件箱，让它们按新模式冷启动重建（只在实验开关里调用）。 */
    public int clearInboxes() {
        List<String> found = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(keys.prefix() + "feed:inbox:*").count(1000).build();
        try (Cursor<String> c = redis.scan(options)) {
            c.forEachRemaining(found::add);
        }
        for (int i = 0; i < found.size(); i += 1000) {
            redis.delete(found.subList(i, Math.min(found.size(), i + 1000)));
        }
        return found.size();
    }

    /** 测试和压测报告用：收件箱里的文章 ID（不含占位）。 */
    public List<Long> inboxSnapshot(Long userId) {
        Set<String> members = redis.opsForZSet().reverseRange(keys.feedInbox(userId), 0, -1);
        if (members == null) {
            return List.of();
        }
        return members.stream().filter(m -> !SENTINEL.equals(m)).map(Long::valueOf).toList();
    }
}
