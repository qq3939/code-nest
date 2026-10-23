package io.github.qq3939.codenest.service.search;

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
    // format = {} 不能省：format 默认是 date_optional_time 等，写入时用第一个 format 而不是 pattern，
    // 它的时间部分带时区，LocalDateTime 没有时区，只会写出 "2026-10-23"，ES 按 mapping 解析失败（P-022）
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime publishTime;
    private String status;
}
