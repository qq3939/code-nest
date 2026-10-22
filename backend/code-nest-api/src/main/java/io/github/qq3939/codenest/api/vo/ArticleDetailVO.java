package io.github.qq3939.codenest.api.vo;

import java.time.LocalDateTime;
import java.util.List;

/** liked/collected/authorFollowed 是相对当前登录用户的状态，未登录时都为 false。 */
public record ArticleDetailVO(Long id, String title, String summary, String coverUrl, String content,
                              CategoryVO category, List<TagVO> tags, UserBriefVO author, String status,
                              LocalDateTime publishTime, LocalDateTime updateTime, ArticleCountVO counts,
                              boolean liked, boolean collected, boolean authorFollowed) {
}
