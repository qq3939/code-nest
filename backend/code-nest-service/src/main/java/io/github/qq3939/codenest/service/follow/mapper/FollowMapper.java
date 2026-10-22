package io.github.qq3939.codenest.service.follow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qq3939.codenest.service.follow.entity.FollowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FollowMapper extends BaseMapper<FollowDO> {

    @Update("UPDATE follow SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int switchStatus(@Param("id") Long id, @Param("from") int from, @Param("to") int to);

    /** 扇出时按 follow.id 游标分批读粉丝，比 LIMIT offset 稳定：offset 越大越慢，且中途有人关注会错位。 */
    @Select("SELECT id, follower_id FROM follow WHERE followee_id = #{followeeId} AND status = 1 AND id > #{afterId} "
            + "ORDER BY id LIMIT #{limit}")
    List<FollowDO> selectFollowersAfter(@Param("followeeId") Long followeeId, @Param("afterId") Long afterId,
                                        @Param("limit") int limit);

    /** hybrid 模式：该用户关注的作者里，粉丝数达到阈值的大 V。 */
    @Select("SELECT f.followee_id FROM follow f JOIN user_stat s ON s.user_id = f.followee_id "
            + "WHERE f.follower_id = #{userId} AND f.status = 1 AND s.follower_count >= #{threshold}")
    List<Long> selectFollowedBigV(@Param("userId") Long userId, @Param("threshold") int threshold);
}
