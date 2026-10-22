package io.github.changeme.projecta.service.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.changeme.projecta.service.article.ArticleTagRow;
import io.github.changeme.projecta.service.article.entity.TagDO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface TagMapper extends BaseMapper<TagDO> {

    /** 一次查出多篇文章的标签，避免列表页 N+1。 */
    List<ArticleTagRow> selectByArticleIds(@Param("articleIds") Collection<Long> articleIds);

    int insertIgnore(@Param("tag") TagDO tag);
}
