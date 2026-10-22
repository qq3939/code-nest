package io.github.qq3939.codenest.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qq3939.codenest.service.user.entity.UserStatDO;
import org.apache.ibatis.annotations.Param;

public interface UserStatMapper extends BaseMapper<UserStatDO> {

    /** field 只能是 article/follower/following，XML 里用 choose 选固定列名，而不是 ${} 拼接。 */
    int increase(@Param("userId") Long userId, @Param("field") String field, @Param("delta") long delta);

    /** 用 article_stat 汇总出作者的获赞数和阅读数，写回 user_stat。 */
    int refreshReceived(@Param("userId") Long userId);
}
