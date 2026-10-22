package io.github.changeme.projecta.service.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("collect_record")
public class CollectRecordDO extends BaseDO {
    private Long userId;
    private Long articleId;
    private Integer status;
}
