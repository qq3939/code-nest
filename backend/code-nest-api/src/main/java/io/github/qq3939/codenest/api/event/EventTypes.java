package io.github.qq3939.codenest.api.event;

/** 事件类型，同时也是 RabbitMQ 的路由键。 */
public final class EventTypes {
    public static final String ARTICLE_PUBLISHED = "article.published";
    public static final String ARTICLE_UPDATED = "article.updated";
    public static final String ARTICLE_REMOVED = "article.removed";
    public static final String INTERACTION_LIKED = "interaction.liked";
    public static final String INTERACTION_COLLECTED = "interaction.collected";
    public static final String COMMENT_CREATED = "comment.created";
    public static final String COMMENT_REPLIED = "comment.replied";
    public static final String USER_FOLLOWED = "user.followed";
    public static final String SYSTEM_NOTICE = "system.notice";
    public static final String CACHE_DELETE = "cache.delete";

    private EventTypes() {
    }
}
