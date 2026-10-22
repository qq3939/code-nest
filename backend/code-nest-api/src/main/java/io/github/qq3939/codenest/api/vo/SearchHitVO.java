package io.github.qq3939.codenest.api.vo;

import java.time.LocalDateTime;
import java.util.List;

/** title、summary、contentSnippet 里可能带 <em> 高亮标签，前端用 DOMPurify 清洗后再渲染。 */
public record SearchHitVO(Long id, String title, String summary, String contentSnippet, List<String> tags,
                          String authorName, Long authorId, LocalDateTime publishTime) {
}
