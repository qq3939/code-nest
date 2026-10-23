package io.github.qq3939.codenest.service.search;

import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试不启动 ES，这里用 Spring Data 自己的转换器检查写进 ES 的字段格式（P-022）：
 * publishTime 必须是 mapping 里声明的 uuuu-MM-dd'T'HH:mm:ss.SSS，否则 ES 拒收整篇文档。
 */
class ArticleDocMappingTest {

    private final MappingElasticsearchConverter converter = converter();

    @Test
    void publishTimeKeepsTimeAndMillis() {
        ArticleDoc doc = new ArticleDoc();
        doc.setId(1L);
        doc.setPublishTime(LocalDateTime.of(2026, 10, 23, 11, 37, 0, 123_000_000));

        Document written = Document.create();
        converter.write(doc, written);

        assertThat(written.get("publishTime")).isEqualTo("2026-10-23T11:37:00.123");
        assertThat(converter.read(ArticleDoc.class, written).getPublishTime()).isEqualTo(doc.getPublishTime());
    }

    private static MappingElasticsearchConverter converter() {
        MappingElasticsearchConverter c = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
        c.afterPropertiesSet();
        return c;
    }
}
