package io.github.changeme.projecta.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.changeme.projecta.api.event.EventMessage;
import io.github.changeme.projecta.api.event.EventTypes;
import io.github.changeme.projecta.api.event.Payloads;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.mq.ConsumerSupport;
import io.github.changeme.projecta.core.mq.MqNames;
import io.github.changeme.projecta.service.cache.CacheService;
import io.github.changeme.projecta.service.feed.FeedService;
import io.github.changeme.projecta.service.notify.NotifyService;
import io.github.changeme.projecta.service.rank.RankService;
import io.github.changeme.projecta.service.search.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 五个业务队列的消费者（SPEC 第 8 节）。公共流程（手动 ack、重试、死信）在 ConsumerSupport 里，
 * 这里只写"收到某类事件做什么"，以及各自的幂等方式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumers {
    private final ConsumerSupport support;
    private final ObjectMapper objectMapper;
    private final NotifyService notifyService;
    private final FeedService feedService;
    private final SearchIndexService searchIndexService;
    private final RankService rankService;
    private final CacheService cacheService;
    private final AppProperties props;

    /** 幂等：notify_message.msg_id 唯一索引。 */
    @RabbitListener(queues = MqNames.NOTIFY_QUEUE)
    public void onNotify(Message message, Channel channel) throws IOException {
        support.handle(MqNames.NOTIFY_QUEUE, message, channel, notifyService::handle);
    }

    /** 幂等：ZADD / ZREM 本身就是幂等的，重复执行结果相同。 */
    @RabbitListener(queues = MqNames.FEED_QUEUE)
    public void onFeed(Message message, Channel channel) throws IOException {
        support.handle(MqNames.FEED_QUEUE, message, channel, event -> {
            switch (event.eventType()) {
                case EventTypes.ARTICLE_PUBLISHED -> feedService.onPublished(article(event).articleId());
                case EventTypes.ARTICLE_REMOVED -> {
                    Payloads.ArticleEvent p = article(event);
                    feedService.onRemoved(p.articleId(), p.authorId());
                }
                case EventTypes.USER_FOLLOWED -> {
                    Payloads.FollowEvent p = objectMapper.convertValue(event.payload(), Payloads.FollowEvent.class);
                    feedService.onFollowChanged(p.followerId(), p.followeeId());
                }
                default -> log.debug("feed ignores {}", event.eventType());
            }
        });
    }

    /** 幂等：按文章 ID upsert / delete。 */
    @RabbitListener(queues = MqNames.SEARCH_QUEUE)
    public void onSearch(Message message, Channel channel) throws IOException {
        support.handle(MqNames.SEARCH_QUEUE, message, channel, event -> {
            Payloads.ArticleEvent p = article(event);
            if (EventTypes.ARTICLE_REMOVED.equals(event.eventType())) {
                searchIndexService.delete(p.articleId());
            } else {
                searchIndexService.upsert(p.articleId());
            }
        });
    }

    /** 幂等：rank:dedup:{msgId} 的 SET NX 和加分在同一个 Lua 脚本里。 */
    @RabbitListener(queues = MqNames.RANK_QUEUE)
    public void onRank(Message message, Channel channel) throws IOException {
        support.handle(MqNames.RANK_QUEUE, message, channel, this::addRankScore);
    }

    /** 幂等：删除缓存本身就是幂等的。 */
    @RabbitListener(queues = MqNames.CACHE_RETRY_QUEUE)
    public void onCacheRetry(Message message, Channel channel) throws IOException {
        support.handle(MqNames.CACHE_RETRY_QUEUE, message, channel, event -> cacheService.deleteForRetry(
                objectMapper.convertValue(event.payload(), Payloads.CacheDeleteEvent.class).keys()));
    }

    private void addRankScore(EventMessage event) {
        AppProperties.Rank score = props.getRank();
        switch (event.eventType()) {
            case EventTypes.ARTICLE_PUBLISHED -> {
                Payloads.ArticleEvent p = article(event);
                if (!p.restored()) {
                    rankService.addActiveScore(event.msgId(), p.authorId(), score.getScorePublish());
                }
            }
            case EventTypes.COMMENT_CREATED -> {
                Payloads.CommentEvent p = objectMapper.convertValue(event.payload(), Payloads.CommentEvent.class);
                rankService.addActiveScore(event.msgId(), p.userId(), score.getScoreComment());
            }
            case EventTypes.INTERACTION_LIKED -> {
                Payloads.LikeEvent p = objectMapper.convertValue(event.payload(), Payloads.LikeEvent.class);
                // 只有文章被点赞算作者活跃；自己赞自己不加分，防止刷榜
                if ("ARTICLE".equals(p.targetType()) && !p.userId().equals(p.ownerId())) {
                    rankService.addActiveScore(event.msgId(), p.ownerId(), score.getScoreLiked());
                }
            }
            case EventTypes.INTERACTION_COLLECTED -> {
                Payloads.CollectEvent p = objectMapper.convertValue(event.payload(), Payloads.CollectEvent.class);
                if (!p.userId().equals(p.ownerId())) {
                    rankService.addActiveScore(event.msgId(), p.ownerId(), score.getScoreCollected());
                }
            }
            default -> log.debug("rank ignores {}", event.eventType());
        }
    }

    private Payloads.ArticleEvent article(EventMessage event) {
        return objectMapper.convertValue(event.payload(), Payloads.ArticleEvent.class);
    }
}
