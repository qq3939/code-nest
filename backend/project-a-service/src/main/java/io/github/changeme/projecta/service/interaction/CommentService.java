package io.github.changeme.projecta.service.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.changeme.projecta.api.common.CursorPage;
import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.api.dto.CommentCreateReq;
import io.github.changeme.projecta.api.event.EventTypes;
import io.github.changeme.projecta.api.event.Payloads;
import io.github.changeme.projecta.api.vo.CommentVO;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.core.util.Times;
import io.github.changeme.projecta.service.article.ArticleService;
import io.github.changeme.projecta.service.article.entity.ArticleDO;
import io.github.changeme.projecta.service.common.AfterCommit;
import io.github.changeme.projecta.service.count.CountField;
import io.github.changeme.projecta.service.count.CountService;
import io.github.changeme.projecta.service.count.IdCount;
import io.github.changeme.projecta.service.interaction.entity.CommentDO;
import io.github.changeme.projecta.service.interaction.mapper.CommentMapper;
import io.github.changeme.projecta.service.outbox.OutboxService;
import io.github.changeme.projecta.service.sensitive.SensitiveWordService;
import io.github.changeme.projecta.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 两级评论：一级评论 parent_id = 0；对任何评论的回复都挂到它所属的一级评论下（楼中楼不再嵌套），
 * 用 reply_to_user_id 记住"回复的是谁"，前端显示成"A 回复 B"。
 */
@Service
@RequiredArgsConstructor
public class CommentService {
    private static final int PREVIEW_REPLIES = 3;

    private final CommentMapper commentMapper;
    private final ArticleService articleService;
    private final SensitiveWordService sensitiveWordService;
    private final CountService countService;
    private final OutboxService outboxService;
    private final UserService userService;
    private final LikeService likeService;

    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(Long userId, Long articleId, CommentCreateReq req) {
        ArticleDO article = articleService.requirePublished(articleId);
        CommentDO comment = new CommentDO();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setContent(sensitiveWordService.replaceAll(req.content().trim()));
        comment.setLikeCount(0L);
        comment.setDeleted(0);
        CommentDO target = null;
        if (req.replyCommentId() != null) {
            target = commentMapper.selectById(req.replyCommentId());
            if (target == null || !target.getArticleId().equals(articleId)) {
                throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
            }
            comment.setParentId(target.getParentId() == 0 ? target.getId() : target.getParentId());
            comment.setReplyToUserId(target.getUserId());
        } else {
            comment.setParentId(0L);
            comment.setReplyToUserId(0L);
        }
        commentMapper.insert(comment);

        // 被回复的人如果正好是文章作者，只给他发"回复"通知，不再重复发"评论"通知
        Long authorReceiver = target != null && target.getUserId().equals(article.getAuthorId()) ? 0L : article.getAuthorId();
        outboxService.save(EventTypes.COMMENT_CREATED, new Payloads.CommentEvent(comment.getId(), articleId, userId,
                authorReceiver, comment.getParentId(), abbreviate(comment.getContent())));
        if (target != null) {
            outboxService.save(EventTypes.COMMENT_REPLIED, new Payloads.CommentEvent(comment.getId(), articleId, userId,
                    target.getUserId(), comment.getParentId(), abbreviate(comment.getContent())));
        }
        AfterCommit.run(() -> countService.increase(articleId, CountField.COMMENT, 1));

        Map<Long, UserBriefVO> users = userService.briefs(List.of(userId, comment.getReplyToUserId()));
        return toVO(comment, users, Set.of(), List.of(), 0);
    }

    /** 删除一级评论时连同它下面的回复一起删，避免回复变成"孤儿"。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long commentId, boolean asAdmin) {
        CommentDO comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (!asAdmin && !comment.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.COMMENT_NO_PERMISSION);
        }
        long removed = commentMapper.deleteById(commentId);
        if (comment.getParentId() == 0) {
            removed += commentMapper.delete(Wrappers.<CommentDO>lambdaQuery().eq(CommentDO::getParentId, commentId));
        }
        long delta = removed;
        AfterCommit.run(() -> countService.increase(comment.getArticleId(), CountField.COMMENT, -delta));
    }

    /** 一级评论，最新的在前；每条附带最早的 3 条回复和回复总数。 */
    public CursorPage<CommentVO> listTop(Long articleId, String cursor, int size, Long viewerId) {
        CursorCodec.Position pos = CursorCodec.decode(cursor);
        LambdaQueryWrapper<CommentDO> query = Wrappers.<CommentDO>lambdaQuery()
                .eq(CommentDO::getArticleId, articleId)
                .eq(CommentDO::getParentId, 0L);
        if (pos != null) {
            LocalDateTime t = toTime(pos.time());
            query.and(w -> w.lt(CommentDO::getCreateTime, t)
                    .or(x -> x.eq(CommentDO::getCreateTime, t).lt(CommentDO::getId, pos.id())));
        }
        query.orderByDesc(CommentDO::getCreateTime).orderByDesc(CommentDO::getId).last("LIMIT " + (size + 1));
        List<CommentDO> rows = commentMapper.selectList(query);
        boolean hasMore = rows.size() > size;
        List<CommentDO> page = hasMore ? rows.subList(0, size) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        List<Long> topIds = page.stream().map(CommentDO::getId).toList();
        List<CommentDO> previews = commentMapper.selectFirstReplies(topIds, PREVIEW_REPLIES);
        Map<Long, Long> replyCounts = commentMapper.countReplies(topIds).stream()
                .collect(Collectors.toMap(IdCount::id, IdCount::cnt));
        List<CommentDO> all = new ArrayList<>(page);
        all.addAll(previews);
        Map<Long, UserBriefVO> users = userService.briefs(userIdsOf(all));
        Set<Long> liked = likeService.likedComments(viewerId, all.stream().map(CommentDO::getId).toList());
        Map<Long, List<CommentDO>> byParent = previews.stream().collect(Collectors.groupingBy(CommentDO::getParentId));

        List<CommentVO> items = page.stream().map(c -> toVO(c, users, liked,
                byParent.getOrDefault(c.getId(), List.of()).stream().map(r -> toVO(r, users, liked, List.of(), 0)).toList(),
                replyCounts.getOrDefault(c.getId(), 0L))).toList();
        CommentDO last = page.get(page.size() - 1);
        return new CursorPage<>(items, hasMore ? CursorCodec.encode(toMillis(last.getCreateTime()), last.getId()) : null, hasMore);
    }

    /** 某条一级评论下的回复，按时间正序，接在预览的 3 条后面继续加载。 */
    public CursorPage<CommentVO> listReplies(Long parentId, String cursor, int size, Long viewerId) {
        CursorCodec.Position pos = CursorCodec.decode(cursor);
        LambdaQueryWrapper<CommentDO> query = Wrappers.<CommentDO>lambdaQuery().eq(CommentDO::getParentId, parentId);
        if (pos != null) {
            LocalDateTime t = toTime(pos.time());
            query.and(w -> w.gt(CommentDO::getCreateTime, t)
                    .or(x -> x.eq(CommentDO::getCreateTime, t).gt(CommentDO::getId, pos.id())));
        }
        query.orderByAsc(CommentDO::getCreateTime).orderByAsc(CommentDO::getId).last("LIMIT " + (size + 1));
        List<CommentDO> rows = commentMapper.selectList(query);
        boolean hasMore = rows.size() > size;
        List<CommentDO> page = hasMore ? rows.subList(0, size) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        Map<Long, UserBriefVO> users = userService.briefs(userIdsOf(page));
        Set<Long> liked = likeService.likedComments(viewerId, page.stream().map(CommentDO::getId).toList());
        CommentDO last = page.get(page.size() - 1);
        return new CursorPage<>(page.stream().map(c -> toVO(c, users, liked, List.of(), 0)).toList(),
                hasMore ? CursorCodec.encode(toMillis(last.getCreateTime()), last.getId()) : null, hasMore);
    }

    private static Set<Long> userIdsOf(List<CommentDO> comments) {
        Set<Long> ids = new HashSet<>();
        comments.forEach(c -> {
            ids.add(c.getUserId());
            if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0) {
                ids.add(c.getReplyToUserId());
            }
        });
        return ids;
    }

    private static CommentVO toVO(CommentDO c, Map<Long, UserBriefVO> users, Set<Long> liked, List<CommentVO> replies,
                                  long replyCount) {
        UserBriefVO replyTo = c.getReplyToUserId() == null || c.getReplyToUserId() == 0 ? null : users.get(c.getReplyToUserId());
        return new CommentVO(c.getId(), c.getArticleId(), c.getParentId(), users.get(c.getUserId()), replyTo,
                c.getContent(), c.getLikeCount() == null ? 0 : c.getLikeCount(), liked.contains(c.getId()), false,
                c.getCreateTime(), replies, replyCount);
    }

    private static String abbreviate(String content) {
        return content.length() > 100 ? content.substring(0, 100) + "…" : content;
    }

    private static long toMillis(LocalDateTime time) {
        return Times.toMillis(time);
    }

    private static LocalDateTime toTime(long millis) {
        return Times.fromMillis(millis);
    }
}
