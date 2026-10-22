package io.github.changeme.projecta.service.search;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ES 里的文章文档。索引名在运行时由 IndexCoordinates 指定，createIndex=false：
 * 不让 Spring Data 按注解自动建索引，因为要用 resources/es 下手写的 IK 分词 mapping。
 */
@Data
@Document(indexName = "article", createIndex = false)
public class ArticleDoc {
    @Id
    private Long id;
    private String title;
    private String summary;
    private String content;
    private List<String> tags;
    private Long authorId;
    private String authorName;
    @Field(type = FieldType.Date, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime publishTime;
    private String status;
}
