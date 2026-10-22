package io.github.qq3939.codenest.api.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleListItemVO(Long id, String title, String summary, String coverUrl, CategoryVO category,
                                List<TagVO> tags, UserBriefVO author, String status, LocalDateTime publishTime,
                                LocalDateTime updateTime, ArticleCountVO counts) {
}
