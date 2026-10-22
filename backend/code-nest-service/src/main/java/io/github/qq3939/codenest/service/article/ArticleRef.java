package io.github.qq3939.codenest.service.article;

import java.time.LocalDateTime;

/** 只带 ID、作者和发布时间的轻量结果，Feed 建收件箱、算热度时用，不必查整行。 */
public record ArticleRef(Long id, Long authorId, LocalDateTime publishTime) {
}
