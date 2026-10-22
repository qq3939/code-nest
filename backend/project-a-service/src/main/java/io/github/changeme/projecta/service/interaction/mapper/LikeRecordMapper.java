package io.github.changeme.projecta.service.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.changeme.projecta.service.interaction.entity.LikeRecordDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface LikeRecordMapper extends BaseMapper<LikeRecordDO> {

    /** 带上旧状态做条件：只有真正发生"0 -> 1"或"1 -> 0"的那一次返回 1，重复点击返回 0，计数不会被多加。 */
    @Update("UPDATE like_record SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int switchStatus(@Param("id") Long id, @Param("from") int from, @Param("to") int to);
}
