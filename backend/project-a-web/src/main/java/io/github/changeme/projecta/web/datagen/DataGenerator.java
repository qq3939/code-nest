package io.github.changeme.projecta.web.datagen;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.service.rank.RankService;
import io.github.changeme.projecta.service.search.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 造数工具（SPEC 14.1）：只在 datagen profile 下启用，跑完自动退出。
 * <pre>
 *   cd backend/project-a-web
 *   java -jar target/app.jar --spring.profiles.active=dev,datagen
 * </pre>
 * 规模可用 app.datagen.* 覆盖。用 JdbcTemplate 批量插入（连接参数里有 rewriteBatchedStatements=true，
 * MySQL 驱动会把一批 INSERT 改写成一条多值 INSERT），比逐条走 MyBatis 快一两个数量级。
 * 计数表最后用聚合 SQL 一次算出，保证和明细表一致。
 */
@Slf4j
@Component
@Profile("datagen")
@RequiredArgsConstructor
public class DataGenerator implements CommandLineRunner {
    private static final String USER_PREFIX = "dg_";
    private static final int BATCH = 2000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final AppProperties props;
    private final RankService rankService;
    private final SearchIndexService searchIndexService;
    private final ApplicationContext context;

    @Value("${app.datagen.users:10000}")
    private int userCount;
    @Value("${app.datagen.articles:50000}")
    private int articleCount;
    @Value("${app.datagen.follows:300000}")
    private int followCount;
    @Value("${app.datagen.big-v:4}")
    private int bigVCount;
    @Value("${app.datagen.big-v-followers:6000}")
    private int bigVFollowers;
    @Value("${app.datagen.likes:300000}")
    private int likeCount;
    @Value("${app.datagen.collects:60000}")
    private int collectCount;
    @Value("${app.datagen.comments:100000}")
    private int commentCount;
    @Value("${app.datagen.password:password-123}")
    private String password;
    @Value("${app.datagen.exit:true}")
    private boolean exitWhenDone;

    /** 固定种子：每次造出来的数据分布一样，压测结果才能对比。 */
    private final Random random = new Random(20261022L);
    private final LocalDateTime now = LocalDateTime.now().withNano(0);

    @Override
    public void run(String... args) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE username LIKE 'dg\\_%'", Integer.class);
        if (existing != null && existing > 0) {
            log.warn("datagen: found {} generated users, skip. Drop the database volume to regenerate.", existing);
            exit();
            return;
        }
        long start = System.currentTimeMillis();
        List<Long> users = users();
        List<Long> tags = tags();
        List<long[]> articles = articles(users, tags);
        follows(users);
        likes(users, articles);
        collects(users, articles);
        comments(users, articles);
        stats();
        warmUp(articles);
        log.info("datagen: all done in {}s. Test password for every dg_ user: {}",
                (System.currentTimeMillis() - start) / 1000, password);
        exit();
    }

    // ---------------------------------------------------------------- users

    private List<Long> users() {
        // BCrypt 很慢（约 80ms 一次），1 万个用户共用一个哈希，否则光算哈希就要十几分钟
        String hash = passwordEncoder.encode(password);
        List<Long> ids = new ArrayList<>(userCount);
        List<Object[]> rows = new ArrayList<>(userCount);
        for (int i = 1; i <= userCount; i++) {
            long id = IdWorker.getId();
            ids.add(id);
            String bio = i <= bigVCount ? "大 V 作者，专注后端架构" : "";
            rows.add(new Object[]{id, String.format("%suser%05d", USER_PREFIX, i), hash,
                    i <= bigVCount ? "大V作者" + i : "测试用户" + i, bio, ts(randomTime(365))});
        }
        batch("INSERT INTO user (id, username, password_hash, nickname, bio, create_time) VALUES (?, ?, ?, ?, ?, ?)", rows);
        log.info("datagen: {} users (password: {})", userCount, password);
        return ids;
    }

    // ---------------------------------------------------------------- tags

    private static final String[] TAG_NAMES = {"Java", "Spring Boot", "MySQL", "Redis", "RabbitMQ", "Elasticsearch",
            "Docker", "Kubernetes", "Vue", "TypeScript", "React", "Go", "Python", "Linux", "Nginx", "JVM", "并发",
            "分布式", "微服务", "缓存", "消息队列", "数据库", "算法", "数据结构", "设计模式", "面试", "源码", "性能优化",
            "架构", "Netty", "MyBatis", "Git", "CI/CD", "云原生", "机器学习", "大模型", "前端工程化", "网络", "操作系统", "安全"};

    private List<Long> tags() {
        Map<String, Long> existing = new HashMap<>();
        jdbc.query("SELECT id, name FROM tag", rs -> {
            existing.put(rs.getString("name"), rs.getLong("id"));
        });
        List<Long> ids = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (String name : TAG_NAMES) {
            Long id = existing.get(name);
            if (id == null) {
                id = IdWorker.getId();
                rows.add(new Object[]{id, name});
            }
            ids.add(id);
        }
        batch("INSERT INTO tag (id, name) VALUES (?, ?)", rows);
        return ids;
    }

    // ---------------------------------------------------------------- articles

    /** 返回 [articleId, authorId, publishTimeMillis]。 */
    private List<long[]> articles(List<Long> users, List<Long> tags) {
        List<Long> categoryIds = jdbc.queryForList("SELECT id FROM category ORDER BY sort", Long.class);
        List<long[]> result = new ArrayList<>(articleCount);
        List<Object[]> articleRows = new ArrayList<>(BATCH);
        List<Object[]> contentRows = new ArrayList<>(BATCH);
        List<Object[]> tagRows = new ArrayList<>(BATCH * 3);
        for (int i = 0; i < articleCount; i++) {
            long id = IdWorker.getId();
            // 作者分布有偏：大 V 发文多，前 10% 的用户贡献约一半文章，更接近真实社区
            long author = i % 50 == 0 ? users.get(random.nextInt(bigVCount))
                    : random.nextBoolean() ? users.get(random.nextInt(Math.max(1, userCount / 10))) : pick(users);
            LocalDateTime publish = randomTime(180);
            String content = content(500 + random.nextInt(1501));
            String title = title();
            articleRows.add(new Object[]{id, author, title, summary(content), categoryIds.get(random.nextInt(categoryIds.size())),
                    "PUBLISHED", ts(publish), ts(publish), ts(publish)});
            contentRows.add(new Object[]{id, content});
            Set<Long> chosen = new HashSet<>();
            int n = 1 + random.nextInt(3);
            while (chosen.size() < n) {
                chosen.add(pick(tags));
            }
            chosen.forEach(t -> tagRows.add(new Object[]{id, t}));
            result.add(new long[]{id, author, java.sql.Timestamp.valueOf(publish).getTime()});
            if (articleRows.size() == BATCH) {
                flushArticles(articleRows, contentRows, tagRows);
            }
        }
        flushArticles(articleRows, contentRows, tagRows);
        log.info("datagen: {} articles", articleCount);
        return result;
    }

    private void flushArticles(List<Object[]> articles, List<Object[]> contents, List<Object[]> tags) {
        batch("INSERT INTO article (id, author_id, title, summary, category_id, status, publish_time, create_time, update_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", articles);
        batch("INSERT INTO article_content (article_id, content) VALUES (?, ?)", contents);
        batch("INSERT INTO article_tag (article_id, tag_id) VALUES (?, ?)", tags);
        articles.clear();
        contents.clear();
        tags.clear();
    }

    // ---------------------------------------------------------------- follows

    private void follows(List<Long> users) {
        Set<Long> seen = new HashSet<>(followCount * 2);
        List<Object[]> rows = new ArrayList<>(BATCH);
        int total = 0;
        // 先给大 V 各配 bigVFollowers 个粉丝，保证超过 Feed 的大 V 阈值（默认 5000）
        for (int v = 0; v < bigVCount; v++) {
            int bigVIndex = v;
            int added = 0;
            while (added < bigVFollowers) {
                int follower = random.nextInt(userCount);
                if (follower == bigVIndex || !seen.add(pairKey(follower, bigVIndex))) {
                    continue;
                }
                rows.add(followRow(users.get(follower), users.get(bigVIndex)));
                added++;
                total++;
                if (rows.size() == BATCH) {
                    batch(FOLLOW_SQL, rows);
                    rows.clear();
                }
            }
        }
        while (total < followCount) {
            int a = random.nextInt(userCount);
            int b = random.nextInt(userCount);
            if (a == b || !seen.add(pairKey(a, b))) {
                continue;
            }
            rows.add(followRow(users.get(a), users.get(b)));
            total++;
            if (rows.size() == BATCH) {
                batch(FOLLOW_SQL, rows);
                rows.clear();
            }
        }
        batch(FOLLOW_SQL, rows);
        log.info("datagen: {} follows ({} big V with {} followers each)", total, bigVCount, bigVFollowers);
    }

    private static final String FOLLOW_SQL = "INSERT INTO follow (id, follower_id, followee_id, status, create_time) VALUES (?, ?, ?, 1, ?)";

    private Object[] followRow(long follower, long followee) {
        return new Object[]{IdWorker.getId(), follower, followee, ts(randomTime(180))};
    }

    private long pairKey(int a, int b) {
        return (long) a * userCount + b;
    }

    // ---------------------------------------------------------------- interactions

    /** 活跃榜预热用：日期 -> (用户 -> 分数)，只记最近 30 天。 */
    private final Map<LocalDate, Map<Long, Double>> activeScores = new HashMap<>();

    private void likes(List<Long> users, List<long[]> articles) {
        Set<Long> seen = new HashSet<>(likeCount * 2);
        List<Object[]> rows = new ArrayList<>(BATCH);
        int total = 0;
        while (total < likeCount) {
            int u = random.nextInt(userCount);
            int a = hotBiasedIndex(articles.size());
            if (!seen.add((long) a * userCount + u)) {
                continue;
            }
            long[] article = articles.get(a);
            LocalDateTime at = after(article[2]);
            rows.add(new Object[]{IdWorker.getId(), users.get(u), "ARTICLE", article[0], ts(at)});
            addActive(at, article[1], props.getRank().getScoreLiked());
            total++;
            if (rows.size() == BATCH) {
                batch("INSERT INTO like_record (id, user_id, target_type, target_id, status, create_time) VALUES (?, ?, ?, ?, 1, ?)", rows);
                rows.clear();
            }
        }
        batch("INSERT INTO like_record (id, user_id, target_type, target_id, status, create_time) VALUES (?, ?, ?, ?, 1, ?)", rows);
        log.info("datagen: {} likes", total);
    }

    private void collects(List<Long> users, List<long[]> articles) {
        Set<Long> seen = new HashSet<>(collectCount * 2);
        List<Object[]> rows = new ArrayList<>(BATCH);
        int total = 0;
        while (total < collectCount) {
            int u = random.nextInt(userCount);
            int a = hotBiasedIndex(articles.size());
            if (!seen.add((long) a * userCount + u)) {
                continue;
            }
            long[] article = articles.get(a);
            LocalDateTime at = after(article[2]);
            rows.add(new Object[]{IdWorker.getId(), users.get(u), article[0], ts(at)});
            addActive(at, article[1], props.getRank().getScoreCollected());
            total++;
            if (rows.size() == BATCH) {
                batch("INSERT INTO collect_record (id, user_id, article_id, status, create_time) VALUES (?, ?, ?, 1, ?)", rows);
                rows.clear();
            }
        }
        batch("INSERT INTO collect_record (id, user_id, article_id, status, create_time) VALUES (?, ?, ?, 1, ?)", rows);
        log.info("datagen: {} collects", total);
    }

    private static final String[] COMMENT_TEXTS = {"写得很清楚，收藏了", "请问这里的锁超时怎么设置比较合适？", "实测有效，感谢分享",
            "和我们线上遇到的问题一模一样", "能不能再讲讲压测数据？", "第二部分没太看懂，求补充", "学到了", "这个方案在高并发下会不会有问题？",
            "已关注，期待下一篇", "代码有个小笔误，第三段的变量名"};

    private void comments(List<Long> users, List<long[]> articles) {
        List<Object[]> rows = new ArrayList<>(BATCH);
        // 最近生成的一级评论：回复从这里面挑，保证父评论属于同一篇文章
        List<long[]> recentTop = new ArrayList<>();
        for (int i = 0; i < commentCount; i++) {
            long id = IdWorker.getId();
            long user = pick(users);
            boolean reply = !recentTop.isEmpty() && random.nextInt(10) < 3;
            if (reply) {
                long[] parent = recentTop.get(random.nextInt(recentTop.size()));
                LocalDateTime at = after(parent[3]);
                rows.add(new Object[]{id, parent[1], user, parent[0], parent[2], pickText(), ts(at)});
            } else {
                long[] article = articles.get(hotBiasedIndex(articles.size()));
                LocalDateTime at = after(article[2]);
                rows.add(new Object[]{id, article[0], user, 0L, 0L, pickText(), ts(at)});
                addActive(at, article[1], props.getRank().getScoreComment());
                if (recentTop.size() >= 5000) {
                    recentTop.set(random.nextInt(recentTop.size()), new long[]{id, article[0], user, Timestamp.valueOf(at).getTime()});
                } else {
                    recentTop.add(new long[]{id, article[0], user, Timestamp.valueOf(at).getTime()});
                }
            }
            if (rows.size() == BATCH) {
                batch(COMMENT_SQL, rows);
                rows.clear();
            }
        }
        batch(COMMENT_SQL, rows);
        log.info("datagen: {} comments", commentCount);
    }

    private static final String COMMENT_SQL = "INSERT INTO comment (id, article_id, user_id, parent_id, reply_to_user_id, content, create_time)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)";

    // ---------------------------------------------------------------- stats

    /** 计数表直接从明细表聚合出来；阅读数没有明细，按点赞数的 5–30 倍随机生成。 */
    private void stats() {
        jdbc.update("""
                INSERT INTO article_stat (article_id, read_count, like_count, collect_count, comment_count)
                SELECT a.id,
                       COALESCE(l.c, 0) * (5 + FLOOR(RAND(7) * 25)) + FLOOR(RAND(11) * 50),
                       COALESCE(l.c, 0), COALESCE(c.c, 0), COALESCE(m.c, 0)
                FROM article a
                LEFT JOIN (SELECT target_id, COUNT(*) c FROM like_record WHERE target_type = 'ARTICLE' AND status = 1 GROUP BY target_id) l ON l.target_id = a.id
                LEFT JOIN (SELECT article_id, COUNT(*) c FROM collect_record WHERE status = 1 GROUP BY article_id) c ON c.article_id = a.id
                LEFT JOIN (SELECT article_id, COUNT(*) c FROM comment WHERE deleted = 0 GROUP BY article_id) m ON m.article_id = a.id
                WHERE a.status = 'PUBLISHED' AND a.deleted = 0
                ON DUPLICATE KEY UPDATE read_count = VALUES(read_count), like_count = VALUES(like_count),
                    collect_count = VALUES(collect_count), comment_count = VALUES(comment_count)
                """);
        jdbc.update("""
                INSERT INTO user_stat (user_id, article_count, follower_count, following_count, like_received, read_received)
                SELECT u.id, COALESCE(ac.c, 0), COALESCE(fr.c, 0), COALESCE(fg.c, 0), COALESCE(ac.like_sum, 0), COALESCE(ac.read_sum, 0)
                FROM user u
                LEFT JOIN (SELECT a.author_id, COUNT(*) c, SUM(s.like_count) like_sum, SUM(s.read_count) read_sum
                           FROM article a JOIN article_stat s ON s.article_id = a.id
                           WHERE a.status = 'PUBLISHED' AND a.deleted = 0 GROUP BY a.author_id) ac ON ac.author_id = u.id
                LEFT JOIN (SELECT followee_id, COUNT(*) c FROM follow WHERE status = 1 GROUP BY followee_id) fr ON fr.followee_id = u.id
                LEFT JOIN (SELECT follower_id, COUNT(*) c FROM follow WHERE status = 1 GROUP BY follower_id) fg ON fg.follower_id = u.id
                ON DUPLICATE KEY UPDATE article_count = VALUES(article_count), follower_count = VALUES(follower_count),
                    following_count = VALUES(following_count), like_received = VALUES(like_received), read_received = VALUES(read_received)
                """);
        log.info("datagen: article_stat and user_stat rebuilt from detail tables");
    }

    // ---------------------------------------------------------------- warm up

    private void warmUp(List<long[]> articles) {
        // 发文也计入活跃分
        for (long[] a : articles) {
            addActive(new Timestamp(a[2]).toLocalDateTime(), a[1], props.getRank().getScorePublish());
        }
        // 活跃日榜：每天一个 ZSet，保留 35 天，和 RankService 写入时的 key 与过期时间一致
        redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            activeScores.forEach((day, scores) -> {
                String key = keys.rankActive("day", day.format(DAY));
                scores.forEach((user, score) -> conn.zIncrBy(key, score, String.valueOf(user)));
                conn.expire(key, Duration.ofDays(35).toSeconds());
            });
            return null;
        });
        // 删掉已经合成好的周榜、月榜缓存，下次读取时用新数据重新合成
        redis.delete(redis.keys(keys.prefix() + "rank:active:week:*"));
        redis.delete(redis.keys(keys.prefix() + "rank:active:month:*"));
        int hot = rankService.refreshHotArticles();
        log.info("datagen: active rank warmed ({} days), hot articles: {}", activeScores.size(), hot);
        if (searchIndexService.enabled()) {
            log.info("datagen: rebuilding search index, this may take a few minutes...");
            log.info("datagen: indexed {} articles", searchIndexService.reindexAll());
        } else {
            log.info("datagen: search disabled (SEARCH_ENABLED=false), skip reindex");
        }
    }

    private void addActive(LocalDateTime at, long userId, double score) {
        LocalDate day = at.toLocalDate();
        if (day.isBefore(LocalDate.now().minusDays(29))) {
            return;
        }
        activeScores.computeIfAbsent(day, d -> new HashMap<>()).merge(userId, score, Double::sum);
    }

    // ---------------------------------------------------------------- helpers

    private void batch(String sql, List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += BATCH) {
            jdbc.batchUpdate(sql, rows.subList(i, Math.min(rows.size(), i + BATCH)));
        }
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    private String pickText() {
        return COMMENT_TEXTS[random.nextInt(COMMENT_TEXTS.length)];
    }

    /** 让互动集中在少数文章上（幂律分布），热点文章才"热"：约 20% 的文章拿到大部分互动。 */
    private int hotBiasedIndex(int size) {
        double r = random.nextDouble();
        return (int) Math.min(size - 1, Math.floor(Math.pow(r, 3) * size));
    }

    private LocalDateTime randomTime(int withinDays) {
        return now.minusSeconds((long) (random.nextDouble() * withinDays * 86400));
    }

    /** 发生在某个时间点之后、现在之前的随机时刻。 */
    private LocalDateTime after(long epochMillis) {
        LocalDateTime from = new Timestamp(epochMillis).toLocalDateTime();
        long span = Math.max(1, Duration.between(from, now).toSeconds());
        return from.plusSeconds((long) (random.nextDouble() * span));
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    private static final String[] TITLE_A = {"深入理解", "一文讲透", "从零实现", "实战：", "踩坑记录：", "面试必问：", "图解", "源码分析："};
    private static final String[] TITLE_B = {"Redis 分布式锁", "MySQL 索引下推", "RabbitMQ 死信队列", "Spring 事务传播", "JVM 垃圾回收",
            "Elasticsearch 分词", "缓存击穿与雪崩", "Feed 流推拉结合", "雪花算法", "滑动窗口限流", "Vue 3 响应式原理", "Netty 线程模型",
            "MyBatis 插件机制", "Docker 网络", "Kafka 与 RabbitMQ 对比", "CompletableFuture", "ThreadLocal 内存泄漏", "B+ 树"};

    private String title() {
        return TITLE_A[random.nextInt(TITLE_A.length)] + TITLE_B[random.nextInt(TITLE_B.length)];
    }

    private static final String[] SENTENCES = {"在高并发场景下，这个问题会被放大很多倍。", "我们先来看一个最简单的例子。",
            "这里的关键是保证操作的原子性。", "如果不加锁，两个线程可能同时读到旧值。", "缓存和数据库的一致性一直是个难题。",
            "下面这段代码演示了具体的做法。", "压测结果显示，优化后吞吐量提升了三倍左右。", "需要注意的是，这个参数默认是关闭的。",
            "消息投递至少一次，所以消费者必须做幂等。", "索引的选择性越高，查询效率越好。", "这种写法在单机下没问题，到了集群就不行了。",
            "我们最终选择了推拉结合的方案。", "具体的取舍可以参考官方文档的说明。", "线上排查时，先看监控，再看日志。"};

    private String content(int length) {
        StringBuilder sb = new StringBuilder(length + 100);
        sb.append("## 背景\n\n");
        int section = 0;
        while (sb.length() < length) {
            sb.append(SENTENCES[random.nextInt(SENTENCES.length)]);
            if (random.nextInt(6) == 0) {
                sb.append("\n\n");
            }
            if (sb.length() > (section + 1) * 600) {
                section++;
                sb.append("\n\n## 第 ").append(section + 1).append(" 部分\n\n");
            }
        }
        return sb.toString();
    }

    private static String summary(String content) {
        String plain = content.replaceAll("[#\\n]", " ").replaceAll("\\s+", " ").trim();
        return plain.length() > 120 ? plain.substring(0, 120) : plain;
    }

    private void exit() {
        if (exitWhenDone) {
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }
}
