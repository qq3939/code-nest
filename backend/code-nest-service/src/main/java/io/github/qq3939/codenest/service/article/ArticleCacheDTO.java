package io.github.qq3939.codenest.service.article;

import io.github.qq3939.codenest.api.vo.CategoryVO;
import io.github.qq3939.codenest.api.vo.TagVO;
import io.github.qq3939.codenest.api.vo.UserBriefVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情缓存的内容。刻意不含计数：计数每秒都在变，放进来就得频繁删缓存，缓存几乎没有命中的机会。
 */
public record ArticleCacheDTO(Long id, Long authorId, String title, String summary, String coverUrl, String content,
                              String status, LocalDateTime publishTime, LocalDateTime updateTime,
                              CategoryVO category, List<TagVO> tags, UserBriefVO author) {
}
