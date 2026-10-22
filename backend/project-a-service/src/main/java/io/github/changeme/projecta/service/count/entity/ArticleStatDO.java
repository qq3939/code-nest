package io.github.changeme.projecta.service.count.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
@TableName("article_stat")
public class ArticleStatDO {
    @TableId(type = IdType.INPUT)
    private Long articleId;
    private Long readCount;
    private Long likeCount;
    private Long collectCount;
    private Long commentCount;
}
