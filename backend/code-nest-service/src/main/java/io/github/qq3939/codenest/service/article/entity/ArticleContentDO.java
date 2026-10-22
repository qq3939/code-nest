package io.github.qq3939.codenest.service.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
@TableName("article_content")
public class ArticleContentDO {
    @TableId(type = IdType.INPUT)
    private Long articleId;
    private String content;
}
