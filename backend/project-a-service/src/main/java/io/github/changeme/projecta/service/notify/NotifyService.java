package io.github.changeme.projecta.service.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.changeme.projecta.api.common.CursorPage;
import io.github.changeme.projecta.api.dto.MarkReadReq;
import io.github.changeme.projecta.api.enums.NotifyType;
import io.github.changeme.projecta.api.event.EventMessage;
import io.github.changeme.projecta.api.event.EventTypes;
import io.github.changeme.projecta.api.event.Payloads;
import io.github.changeme.projecta.api.vo.NotifyVO;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.service.article.entity.ArticleDO;
import io.github.changeme.projecta.service.article.mapper.ArticleMapper;
import io.github.changeme.projecta.service.notify.entity.NotifyMessageDO;
import io.github.changeme.projecta.service.notify.mapper.NotifyMessageMapper;
import io.github.changeme.projecta.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 通知：notify.queue 的消费逻辑 + 用户侧的查询、已读。
 * 幂等靠 notify_message.msg_id 唯一索引：同一条消息被投递两次，第二次插入撞索引，直接当作成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {
    private final NotifyMessageMapper notifyMapper;
    private final ArticleMapper articleMapper;
    private final UserService userService;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final ObjectMapper objectMapper;

    /** 返回 true 表示新写入了一条通知。 */
    public boolean handle(EventMessage event) {
        NotifyMessageDO row = build(event);
        if (row == null || row.getReceiverId() == null || row.getReceiverId() == 0
                || row.getReceiverId().equals(row.getSenderId())) {
            // 自己赞自己、取关等情况不产生通知
            return false;
        }
        row.setMsgId(event.msgId());
        row.setIsRead(0);
        try {
            notifyMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.info("duplicate notify message ignored, msgId={}", event.msgId());
            return false;
        }
        refreshUnreadAndPush(row.getReceiverId());
        return true;
    }

    private NotifyMessageDO build(EventMessage event) {
        return switch (event.eventType()) {
            case EventTypes.INTERACTION_LIKED -> {
                var p = objectMapper.convertValue(event.payload(), Payloads.LikeEvent.class);
                String what = "COMMENT".equals(p.targetType()) ? "赞了你的评论" : "赞了你的文章《" + title(p.articleId()) + "》";
                yield row(p.ownerId(), p.userId(), NotifyType.LIKE, p.targetType(), p.articleId(), what);
            }
            case EventTypes.INTERACTION_COLLECTED -> {
                var p = objectMapper.convertValue(event.payload(), Payloads.CollectEvent.class);
                yield row(p.ownerId(), p.userId(), NotifyType.COLLECT, "ARTICLE", p.articleId(),
                        "收藏了你的文章《" + title(p.articleId()) + "》");
            }
            case EventTypes.COMMENT_CREATED -> {
                var p = objectMapper.convertValue(event.payload(), Payloads.CommentEvent.class);
                yield row(p.receiverId(), p.userId(), NotifyType.COMMENT, "ARTICLE", p.articleId(),
                        "评论了你的文章《" + title(p.articleId()) + "》：" + p.content());
            }
            case EventTypes.COMMENT_REPLIED -> {
                var p = objectMapper.convertValue(event.payload(), Payloads.CommentEvent.class);
                yield row(p.receiverId(), p.userId(), NotifyType.REPLY, "ARTICLE", p.articleId(), "回复了你：" + p.content());
            }
            case EventTypes.USER_FOLLOWED -> {
                var p = objectMapper.convertValue(event.payload(), Payloads.FollowEvent.class);
                yield p.followed() ? row(p.followeeId(), p.followerId(), NotifyType.FOLLOW, "USER", p.followerId(), "关注了你") : null;
            }
            case EventTypes.SYSTEM_NOTICE -> {
                var p = objectMapper.convertValue(event.payload(), Payloads.SystemNoticeEvent.class);
                yield row(p.receiverId(), 0L, NotifyType.SYSTEM, "", 0L, p.content());
            }
            default -> null;
        };
    }

    private static NotifyMessageDO row(Long receiver, Long sender, NotifyType type, String targetType, Long targetId,
                                       String content) {
        NotifyMessageDO row = new NotifyMessageDO();
        row.setReceiverId(receiver);
        row.setSenderId(sender);
        row.setType(type);
        row.setTargetType(targetType);
        row.setTargetId(targetId == null ? 0L : targetId);
        row.setContent(content.length() > 500 ? content.substring(0, 500) : content);
        return row;
    }

    private String title(Long articleId) {
        if (articleId == null) {
            return "";
        }
        ArticleDO a = articleMapper.selectById(articleId);
        return a == null ? "已删除的文章" : a.getTitle();
    }

    /** 按 ID 倒序的游标分页（雪花 ID 大致按时间递增，用它当游标比时间更省事且唯一）。 */
    public CursorPage<NotifyVO> list(Long userId, NotifyType type, String cursor, int size) {
        LambdaQueryWrapper<NotifyMessageDO> query = Wrappers.<NotifyMessageDO>lambdaQuery()
                .eq(NotifyMessageDO::getReceiverId, userId)
                .eq(type != null, NotifyMessageDO::getType, type)
                .lt(cursor != null && !cursor.isBlank(), NotifyMessageDO::getId, parseCursor(cursor))
                .orderByDesc(NotifyMessageDO::getId)
                .last("LIMIT " + (size + 1));
        List<NotifyMessageDO> rows = notifyMapper.selectList(query);
        boolean hasMore = rows.size() > size;
        List<NotifyMessageDO> page = hasMore ? rows.subList(0, size) : rows;
        Map<Long, UserBriefVO> senders = userService.briefs(page.stream().map(NotifyMessageDO::getSenderId)
                .filter(id -> id != null && id != 0).toList());
        List<NotifyVO> items = page.stream().map(n -> new NotifyVO(n.getId(), n.getType().name(),
                senders.get(n.getSenderId()), n.getTargetType(), n.getTargetId(), n.getContent(), n.getIsRead() == 1,
                n.getCreateTime())).toList();
        String next = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CursorPage<>(items, next, hasMore);
    }

    public long unreadCount(Long userId) {
        String cached = redis.opsForValue().get(keys.notifyUnread(userId));
        if (cached != null) {
            return Long.parseLong(cached);
        }
        return refreshUnread(userId);
    }

    public void markRead(Long userId, MarkReadReq req) {
        var update = Wrappers.<NotifyMessageDO>lambdaUpdate()
                .set(NotifyMessageDO::getIsRead, 1)
                .eq(NotifyMessageDO::getReceiverId, userId)
                .eq(NotifyMessageDO::getIsRead, 0);
        if (!req.all()) {
            if (req.ids() == null || req.ids().isEmpty()) {
                return;
            }
            update.in(NotifyMessageDO::getId, req.ids());
        }
        notifyMapper.update(update);
        refreshUnreadAndPush(userId);
    }

    /**
     * 未读数用"从库里重新数一遍再 SET"而不是 INCR：
     * 插入通知和 INCR 不在一个事务里，INCR 失败后重试会因幂等被跳过，计数就永久偏了；重新数则能自我修正。
     * (receiver_id, is_read, create_time) 索引让这个 COUNT 很便宜。
     */
    private long refreshUnread(Long userId) {
        long count = notifyMapper.selectCount(Wrappers.<NotifyMessageDO>lambdaQuery()
                .eq(NotifyMessageDO::getReceiverId, userId).eq(NotifyMessageDO::getIsRead, 0));
        redis.opsForValue().set(keys.notifyUnread(userId), String.valueOf(count));
        return count;
    }

    private void refreshUnreadAndPush(Long userId) {
        long unread = refreshUnread(userId);
        // 广播给所有实例，持有该用户 SSE 连接的那个实例负责推送（见 SseHub）
        redis.convertAndSend(keys.channelNotifyPush(), userId + ":" + unread);
    }

    private static Long parseCursor(String cursor) {
        try {
            return cursor == null || cursor.isBlank() ? null : Long.valueOf(cursor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
