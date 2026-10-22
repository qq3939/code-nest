package io.github.changeme.projecta.service.stat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.changeme.projecta.service.common.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;

import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("daily_stat")
public class DailyStatDO extends BaseDO {
    private LocalDate statDate;
    private Long dau;
    private Long newUsers;
    private Long newArticles;
    /** reads 是 MySQL 保留字，需要反引号 */
    @TableField("`reads`")
    private Long reads;
    private Long likes;
}
