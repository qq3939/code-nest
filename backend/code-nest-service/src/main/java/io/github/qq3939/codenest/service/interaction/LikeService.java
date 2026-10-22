package io.github.qq3939.codenest.service.interaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.enums.TargetType;
import io.github.qq3939.codenest.api.event.EventTypes;
import io.github.qq3939.codenest.api.event.Payloads;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.core.util.Times;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.service.article.ArticleService;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.common.AfterCommit;
import io.github.qq3939.codenest.service.count.CountField;
import io.github.qq3939.codenest.service.count.CountService;
import io.github.qq3939.codenest.service.interaction.entity.CommentDO;
import io.github.qq3939.codenest.service.interaction.entity.LikeRecordDO;
import io.github.qq3939.codenest.service.interaction.mapper.CommentMapper;
import io.github.qq3939.codenest.service.interaction.mapper.LikeRecordMapper;
import io.github.qq3939.codenest.service.outbox.OutboxService;
import io.github.qq3939.codenest.service.stat.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 点赞（文章和评论）。关系表 like_record 是唯一的事实来源；
 * like:article:{id} 这个 ZSet 只是"谁点过赞"的缓存，缺失时从库重建，所以 Redis 丢了也不影响正确性。
 */
@Service
@RequiredArgsConstructor
public class LikeService {
    /** 占位成员：没人点赞的文章也建一个 ZSet，否则每次判断都会因为 key 不存在而去查库。 */
    private static final String SENTINEL = "0";
    private static final Duration LIKERS_TTL = Duration.ofDays(7);

    private final LikeRecordMapper likeMapper;
    private final CommentMapper commentMapper;
    private final ArticleService articleService;
    private final CountService countService;
    private final OutboxService outboxService;
    private final StatService statService;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Long userId, Long articleId) {
        ArticleDO article = articleService.requirePublished(articleId);
        if (!RelationSwitch.activate(likeMapper, find(userId, TargetType.ARTICLE, articleId),
                () -> newRecord(userId, TargetType.ARTICLE, articleId), likeMapper::switchStatus)) {
            return;
        }
        outboxService.save(EventTypes.INTERACTION_LIKED, new Payloads.LikeEvent(userId, TargetType.ARTICLE.name(),
                articleId, article.getAuthorId(), articleId));
        AfterCommit.run(() -> {
            countService.increase(articleId, CountField.LIKE, 1);
            statService.incrDaily("likes", 1);
            String key = keys.articleLikers(articleId);
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                redis.opsForZSet().add(key, String.valueOf(userId), System.currentTimeMillis());
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlikeArticle(Long userId, Long articleId) {
        if (!RelationSwitch.deactivate(find(userId, TargetType.ARTICLE, articleId), likeMapper::switchStatus)) {
            return;
        }
        AfterCommit.run(() -> {
            countService.increase(articleId, CountField.LIKE, -1);
            redis.opsForZSet().remove(keys.articleLikers(articleId), String.valueOf(userId));
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void likeComment(Long userId, Long commentId) {
        CommentDO comment = requireComment(commentId);
        if (!RelationSwitch.activate(likeMapper, find(userId, TargetType.COMMENT, commentId),
                () -> newRecord(userId, TargetType.COMMENT, commentId), likeMapper::switchStatus)) {
            return;
        }
        commentMapper.increaseLike(commentId, 1);
        outboxService.save(EventTypes.INTERACTION_LIKED, new Payloads.LikeEvent(userId, TargetType.COMMENT.name(),
                commentId, comment.getUserId(), comment.getArticleId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlikeComment(Long userId, Long commentId) {
        if (RelationSwitch.deactivate(find(userId, TargetType.COMMENT, commentId), likeMapper::switchStatus)) {
            commentMapper.increaseLike(commentId, -1);
        }
    }

    public boolean isArticleLiked(Long userId, Long articleId) {
        if (userId == null) {
            return false;
        }
        ensureLikers(articleId);
        return redis.opsForZSet().score(keys.articleLikers(articleId), String.valueOf(userId)) != null;
    }

    /** 最近点赞的人（ID），详情页展示头像用。 */
    public List<Long> recentLikers(Long articleId, int limit) {
        ensureLikers(articleId);
        Set<String> members = redis.opsForZSet().reverseRange(keys.articleLikers(articleId), 0, limit);
        if (members == null) {
            return List.of();
        }
        return members.stream().filter(m -> !SENTINEL.equals(m)).limit(limit).map(Long::valueOf).toList();
    }

    public Set<Long> likedComments(Long userId, Collection<Long> commentIds) {
        if (userId == null || commentIds.isEmpty()) {
            return Set.of();
        }
        return likeMapper.selectList(Wrappers.<LikeRecordDO>lambdaQuery()
                        .eq(LikeRecordDO::getUserId, userId)
                        .eq(LikeRecordDO::getTargetType, TargetType.COMMENT)
                        .in(LikeRecordDO::getTargetId, commentIds)
                        .eq(LikeRecordDO::getStatus, 1))
                .stream().map(LikeRecordDO::getTargetId).collect(Collectors.toCollection(HashSet::new));
    }

    private void ensureLikers(Long articleId) {
        String key = keys.articleLikers(articleId);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        List<LikeRecordDO> rows = likeMapper.selectList(Wrappers.<LikeRecordDO>lambdaQuery()
                .select(LikeRecordDO::getUserId, LikeRecordDO::getUpdateTime)
                .eq(LikeRecordDO::getTargetType, TargetType.ARTICLE)
                .eq(LikeRecordDO::getTargetId, articleId)
                .eq(LikeRecordDO::getStatus, 1));
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        tuples.add(ZSetOperations.TypedTuple.of(SENTINEL, 0D));
        rows.forEach(r -> tuples.add(ZSetOperations.TypedTuple.of(String.valueOf(r.getUserId()),
                (double) Times.toMillis(r.getUpdateTime()))));
        redis.opsForZSet().add(key, tuples);
        redis.expire(key, LIKERS_TTL);
    }

    private CommentDO requireComment(Long commentId) {
        CommentDO comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private LikeRecordDO find(Long userId, TargetType type, Long targetId) {
        return likeMapper.selectOne(Wrappers.<LikeRecordDO>lambdaQuery()
                .eq(LikeRecordDO::getUserId, userId)
                .eq(LikeRecordDO::getTargetType, type)
                .eq(LikeRecordDO::getTargetId, targetId));
    }

    private static LikeRecordDO newRecord(Long userId, TargetType type, Long targetId) {
        LikeRecordDO row = new LikeRecordDO();
        row.setUserId(userId);
        row.setTargetType(type);
        row.setTargetId(targetId);
        row.setStatus(1);
        return row;
    }
}
