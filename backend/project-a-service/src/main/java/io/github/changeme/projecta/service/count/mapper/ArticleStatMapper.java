package io.github.changeme.projecta.service.count.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.changeme.projecta.service.count.IdCount;
import io.github.changeme.projecta.service.count.entity.ArticleStatDO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface ArticleStatMapper extends BaseMapper<ArticleStatDO> {

    /** atomic-sql 策略：一条 UPDATE 完成"读-改-写"，由 InnoDB 行锁保证并发正确。 */
    int increase(@Param("articleId") Long articleId, @Param("field") String field, @Param("delta") long delta);

    /** redis-batch 写回：一条 SQL 写多行绝对值。 */
    int upsertBatch(@Param("rows") List<ArticleStatDO> rows);

    int insertIgnore(@Param("articleId") Long articleId);

    List<IdCount> countArticleLikes(@Param("ids") Collection<Long> ids);

    List<IdCount> countCollects(@Param("ids") Collection<Long> ids);

    List<IdCount> countComments(@Param("ids") Collection<Long> ids);
}
