package io.github.changeme.projecta.service.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.changeme.projecta.service.article.entity.ArticleTagDO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

public interface ArticleTagMapper extends BaseMapper<ArticleTagDO> {

    int insertBatchIgnore(@Param("articleId") Long articleId, @Param("tagIds") Collection<Long> tagIds);

    /** 合并标签：把 source 的关联改挂到 target，已经同时挂了两者的文章用 IGNORE 跳过。 */
    int moveTag(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
