package io.github.qq3939.codenest.service.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("category")
public class CategoryDO extends BaseDO {
    private String name;
    private Integer sort;
}
