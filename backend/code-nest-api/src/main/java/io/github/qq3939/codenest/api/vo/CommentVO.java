package io.github.qq3939.codenest.api.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一级评论带 replies（最早的几条回复）和 replyCount；回复本身的 replies 为空列表。
 * replyToUser 只在"回复某条回复"时有值，前端显示为"A 回复 B"。
 */
public record CommentVO(Long id, Long articleId, Long parentId, UserBriefVO user, UserBriefVO replyToUser,
                        String content, long likeCount, boolean liked, boolean deleted, LocalDateTime createTime,
                        List<CommentVO> replies, long replyCount) {
}
