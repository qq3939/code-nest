package io.github.changeme.projecta.service.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.changeme.projecta.service.count.IdCount;
import io.github.changeme.projecta.service.interaction.entity.CommentDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

public interface CommentMapper extends BaseMapper<CommentDO> {

    @Update("UPDATE comment SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{id}")
    int increaseLike(@Param("id") Long id, @Param("delta") long delta);

    /** 每个一级评论下的回复数（含已删除的回复不计）。 */
    List<IdCount> countReplies(@Param("parentIds") Collection<Long> parentIds);

    /** 每个一级评论最早的 n 条回复，用窗口函数一次查完（MySQL 8 起支持）。 */
    List<CommentDO> selectFirstReplies(@Param("parentIds") Collection<Long> parentIds, @Param("n") int n);
}
