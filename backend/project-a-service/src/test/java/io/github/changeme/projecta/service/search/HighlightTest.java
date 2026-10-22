package io.github.changeme.projecta.service.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HighlightTest {

    @Test
    void escapesHtmlBeforeHighlighting() {
        assertThat(SearchService.highlight("Redis <b>实战</b>，redis 入门", "redis"))
                .isEqualTo("<em>Redis</em> &lt;b&gt;实战&lt;/b&gt;，<em>redis</em> 入门");
    }

    @Test
    void keywordWithRegexCharsIsLiteral() {
        assertThat(SearchService.highlight("a.b a+b", "a+b")).isEqualTo("a.b <em>a+b</em>");
    }
}
