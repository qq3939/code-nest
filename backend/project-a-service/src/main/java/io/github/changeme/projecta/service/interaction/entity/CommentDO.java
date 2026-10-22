package io.github.changeme.projecta.service.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("comment")
public class CommentDO extends BaseDO {
    private Long articleId;
    private Long userId;
    /** 0 表示一级评论 */
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private Long likeCount;
    @TableLogic
    private Integer deleted;
}
