package io.github.changeme.projecta.service.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("article_tag")
public class ArticleTagDO {
    private Long articleId;
    private Long tagId;
}
