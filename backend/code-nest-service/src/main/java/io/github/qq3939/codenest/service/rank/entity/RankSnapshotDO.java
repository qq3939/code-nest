package io.github.qq3939.codenest.service.rank.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.qq3939.codenest.service.common.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("rank_snapshot")
public class RankSnapshotDO extends BaseDO {
    private String rankType;
    private String period;
    private Integer rankNo;
    private Long subjectId;
    private BigDecimal score;
}
