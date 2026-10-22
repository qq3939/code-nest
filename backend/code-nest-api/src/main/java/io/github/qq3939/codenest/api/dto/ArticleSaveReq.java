package io.github.qq3939.codenest.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建、修改、自动保存共用的请求体。草稿允许字段不全，所以这里只限长度；
 * 发布时的必填校验放在 Service 里，见 ArticleService#checkPublishable。
 */
public record ArticleSaveReq(
        @Size(max = 128) String title,
        @Size(max = 512) String summary,
        @Size(max = 255) String coverUrl,
        Long categoryId,
        @Size(max = 5, message = "最多 5 个标签") List<@Size(max = 32) String> tags,
        @Size(max = 200_000, message = "正文过长") String content) {
}
