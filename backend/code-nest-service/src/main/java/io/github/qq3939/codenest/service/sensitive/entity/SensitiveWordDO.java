package io.github.qq3939.codenest.service.sensitive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import io.github.qq3939.codenest.api.enums.SensitiveLevel;
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
