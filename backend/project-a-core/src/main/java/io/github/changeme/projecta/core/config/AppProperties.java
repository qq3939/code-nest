package io.github.changeme.projecta.core.config;

import io.github.changeme.projecta.api.enums.CacheMode;
import io.github.changeme.projecta.api.enums.CountStrategy;
import io.github.changeme.projecta.api.enums.FeedMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * app.* 下的全部业务配置。集中在一个类里，读者能一眼看到项目有哪些可调的旋钮；
 * 默认值就是 SPEC 里写的值，application.yml 只覆盖需要按环境区分的部分。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 项目名：Redis key 前缀和 RabbitMQ 交换机前缀都用它，多个项目共用一套中间件时不会冲突。 */
    private String name = "project-a";

    private Jwt jwt = new Jwt();
    private Auth auth = new Auth();
    private Admin admin = new Admin();
    private Count count = new Count();
    private Feed feed = new Feed();
    private Cache cache = new Cache();
    private Search search = new Search();
    private Rank rank = new Rank();
    private Outbox outbox = new Outbox();
    private Upload upload = new Upload();
    private Minio minio = new Minio();
    private DevTools devTools = new DevTools();
    private Mq mq = new Mq();
    /** 限流规则覆盖：key 是 @RateLimit 的 name，没配的用注解上的默认值。 */
    private Map<String, RateLimitRule> rateLimit = new LinkedHashMap<>();

    @Data
    public static class Jwt {
        /** 至少 32 字节，HS256 要求密钥长度不小于摘要长度。 */
        private String secret;
        private Duration accessTtl = Duration.ofMinutes(30);
        private Duration refreshTtl = Duration.ofDays(7);
    }

    @Data
    public static class Auth {
        private int maxLoginFail = 5;
        private Duration lockDuration = Duration.ofMinutes(15);
        private Duration userStatusCacheTtl = Duration.ofMinutes(10);
    }

    @Data
    public static class Admin {
        private String username;
        private String password;
    }

    @Data
    public static class Count {
        private CountStrategy strategy = CountStrategy.REDIS_BATCH;
        private Duration flushInterval = Duration.ofSeconds(10);
        private int flushBatchSize = 500;
    }

    @Data
    public static class Feed {
        private FeedMode mode = FeedMode.HYBRID;
        private int bigVThreshold = 5000;
        private int inboxMaxSize = 500;
        private int outboxMaxSize = 1000;
        private int fanoutBatchSize = 500;
        private int backfillSize = 20;
        private Duration inboxTtl = Duration.ofDays(30);
        private Duration bigVFollowTtl = Duration.ofMinutes(10);
    }

    @Data
    public static class Cache {
        private CacheMode mode = CacheMode.TWO_LEVEL;
        private int localMaxSize = 10_000;
        private Duration localTtl = Duration.ofSeconds(60);
        private Duration articleTtl = Duration.ofMinutes(30);
        private Duration articleTtlJitter = Duration.ofMinutes(5);
        private Duration nullTtl = Duration.ofMinutes(2);
        private Duration hotListLogicalTtl = Duration.ofMinutes(1);
        private Duration rebuildLockTtl = Duration.ofSeconds(3);
    }

    @Data
    public static class Search {
        private boolean enabled = false;
        private String index = "article";
    }

    @Data
    public static class Rank {
        private int scorePublish = 10;
        private int scoreComment = 2;
        private int scoreLiked = 2;
        private int scoreCollected = 3;
        private int hotTopN = 200;
        private int hotWindowDays = 7;
        private int archiveTopN = 100;
    }

    @Data
    public static class Outbox {
        private int maxRetry = 10;
        private int relayBatchSize = 100;
        /** 指数退避的基数：第 n 次失败后等 base * 2^n，上限 maxBackoff。 */
        private Duration baseBackoff = Duration.ofSeconds(5);
        private Duration maxBackoff = Duration.ofMinutes(10);
    }

    @Data
    public static class Upload {
        private long maxSizeBytes = 5L * 1024 * 1024;
        /** 返回给前端的 URL 前缀，由 Nginx 反向代理到 MinIO。 */
        private String publicPrefix = "/files/";
    }

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey;
        private String secretKey;
        private String bucket = "project-a";
    }

    @Data
    public static class DevTools {
        /** 运行时切换策略的接口，只在本地实验环境打开，见 D-016。 */
        private boolean enabled = false;
    }

    @Data
    public static class Mq {
        /** 各级重试队列的延迟，默认 1 秒、5 秒、30 秒；集成测试里调短，免得一个用例等半分钟。 */
        private List<Duration> retryDelays = List.of(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    @Data
    public static class RateLimitRule {
        private Duration window;
        private int limit;
    }
}
