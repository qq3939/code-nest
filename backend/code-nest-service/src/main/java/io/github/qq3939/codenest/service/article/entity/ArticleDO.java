package io.github.qq3939.codenest.service.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import com.baomidou.mybatisplus.annotation.TableLogic;
import io.github.qq3939.codenest.api.enums.ArticleStatus;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("article")
public class ArticleDO extends BaseDO {
    private Long authorId;
    private String title;
    private String summary;
    private String coverUrl;
    private Long categoryId;
    private ArticleStatus status;
    private LocalDateTime publishTime;
    @TableLogic
    private Integer deleted;
}
