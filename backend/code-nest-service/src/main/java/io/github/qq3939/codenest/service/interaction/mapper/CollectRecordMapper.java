package io.github.qq3939.codenest.service.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qq3939.codenest.service.interaction.entity.CollectRecordDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CollectRecordMapper extends BaseMapper<CollectRecordDO> {

    @Update("UPDATE collect_record SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int switchStatus(@Param("id") Long id, @Param("from") int from, @Param("to") int to);
}
