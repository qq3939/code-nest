package io.github.changeme.projecta.service.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("category")
public class CategoryDO extends BaseDO {
    private String name;
    private Integer sort;
}
