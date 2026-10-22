package io.github.changeme.projecta.core.redis;

import io.github.changeme.projecta.core.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * 所有 Redis key 的唯一出处，和 SPEC 第 7 节的表一一对应。
 * 集中管理的好处：改前缀、查"某个 key 在哪里写入"都只需要看这一个文件。
 */
@Component
public class RedisKeys {
    private final String prefix;

    public RedisKeys(AppProperties props) {
        this.prefix = props.getName() + ":";
    }

    public String prefix() {
        return prefix;
    }

    public String refreshToken(Long userId, String tokenId) {
        return prefix + "auth:refresh:" + userId + ":" + tokenId;
    }

    public String refreshTokenPattern(Long userId) {
        return prefix + "auth:refresh:" + userId + ":*";
    }

    public String loginFail(String username) {
        return prefix + "auth:login-fail:" + username;
    }

    public String userStatus(Long userId) {
        return prefix + "user:status:" + userId;
    }

    public String articleCount(Long articleId) {
        return prefix + "count:article:" + articleId;
    }

    public String articleCountDirty() {
        return prefix + "count:dirty:article";
    }

    public String articleLikers(Long articleId) {
        return prefix + "like:article:" + articleId;
    }

    public String feedInbox(Long userId) {
        return prefix + "feed:inbox:" + userId;
    }

    public String feedOutbox(Long authorId) {
        return prefix + "feed:outbox:" + authorId;
    }

    public String feedBigVFollow(Long userId) {
        return prefix + "feed:bigv-follow:" + userId;
    }

    public String rankActive(String period, String date) {
        return prefix + "rank:active:" + period + ":" + date;
    }

    public String rankHotArticle() {
        return prefix + "rank:hot:article";
    }

    public String rankDedup(String msgId) {
        return prefix + "rank:dedup:" + msgId;
    }

    public String articleCache(Long articleId) {
        return prefix + "cache:article:" + articleId;
    }

    public String hotListCache() {
        return prefix + "cache:hot-list";
    }

    public String articleCacheLock(Long articleId) {
        return prefix + "lock:cache:article:" + articleId;
    }

    public String jobLock(String jobName) {
        return prefix + "lock:job:" + jobName;
    }

    public String rateLimit(String name, String dim, String id) {
        return prefix + "ratelimit:" + name + ":" + dim + ":" + id;
    }

    public String submitToken(String token) {
        return prefix + "submit:token:" + token;
    }

    public String uvDay(String date) {
        return prefix + "uv:day:" + date;
    }

    /** 看板用的当日阅读量、点赞量（SPEC 第 7 节之外新增的 key，见 D-017）。 */
    public String statDaily(String date) {
        return prefix + "stat:daily:" + date;
    }

    public String notifyUnread(Long userId) {
        return prefix + "notify:unread:" + userId;
    }

    /** 文章状态缓存，Feed 读时过滤用（SPEC 9.3"状态查询带缓存"）。 */
    public String articleStatus(Long articleId) {
        return prefix + "article:status:" + articleId;
    }

    // ---------- Pub/Sub 频道 ----------

    public String channelCacheInvalidate() {
        return prefix + "cache:invalidate";
    }

    public String channelNotifyPush() {
        return prefix + "notify:push";
    }

    public String channelSensitiveRefresh() {
        return prefix + "sensitive:refresh";
    }

    public String channelStrategyRefresh() {
        return prefix + "strategy:refresh";
    }
}
