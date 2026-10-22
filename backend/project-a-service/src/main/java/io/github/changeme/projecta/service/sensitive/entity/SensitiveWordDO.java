package io.github.changeme.projecta.service.sensitive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import io.github.changeme.projecta.api.enums.SensitiveLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sensitive_word")
public class SensitiveWordDO extends BaseDO {
    private String word;
    private SensitiveLevel level;
    private Integer enabled;
}
