package io.github.qq3939.codenest.api.event;

import java.util.List;

/** 各事件的 payload。放在一个文件里，方便对照 SPEC 第 8 节的路由表阅读。 */
public final class Payloads {

    /**
     * article.published / updated / removed。
     * restored=true 表示"恢复上架/审核后再次发布"，不是首次发布：Feed 照常扇出，但排行不再给作者加发文分。
     */
    public record ArticleEvent(Long articleId, Long authorId, boolean restored) {

        public static ArticleEvent of(Long articleId, Long authorId) {
            return new ArticleEvent(articleId, authorId, false);
        }
    }

    /** interaction.liked：targetType 为 ARTICLE 或 COMMENT；ownerId 是被点赞内容的作者，即通知接收人。 */
    public record LikeEvent(Long userId, String targetType, Long targetId, Long ownerId, Long articleId) {
    }

    /** interaction.collected */
    public record CollectEvent(Long userId, Long articleId, Long ownerId) {
    }

    /** comment.created（receiverId 是文章作者）/ comment.replied（receiverId 是被回复人） */
    public record CommentEvent(Long commentId, Long articleId, Long userId, Long receiverId, Long parentId, String content) {
    }

    /** user.followed：followed=false 表示取关，Feed 消费者据此清理收件箱。 */
    public record FollowEvent(Long followerId, Long followeeId, boolean followed) {
    }

    /** system.notice */
    public record SystemNoticeEvent(Long receiverId, String content) {
    }

    /** cache.delete：删 Redis 缓存失败时的补偿 */
    public record CacheDeleteEvent(List<String> keys) {
    }

    private Payloads() {
    }
}
