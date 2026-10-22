package io.github.qq3939.codenest.service.article;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleSummaryTest {

    @Test
    void stripsMarkdownAndTruncates() {
        String md = "# 标题\n\n**加粗** 和 [链接](http://x) 以及 ![图](a.png)\n```java\ncode\n```\n正文";
        assertThat(ArticleService.autoSummary(md)).isEqualTo("标题 加粗 和 链接 以及 正文");
        assertThat(ArticleService.autoSummary("字".repeat(200))).hasSize(121).endsWith("…");
    }
}
