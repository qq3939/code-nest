package io.github.qq3939.codenest.service.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.qq3939.codenest.service.article.ArticleRef;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ArticleMapper extends BaseMapper<ArticleDO> {

    IPage<ArticleDO> selectPublishedPage(IPage<ArticleDO> page, @Param("categoryId") Long categoryId,
                                         @Param("tagId") Long tagId);

    IPage<ArticleDO> selectCollectedPage(IPage<ArticleDO> page, @Param("userId") Long userId);

    /** 收件箱冷启动：关注的作者（排除大 V）最近发布的文章。 */
    List<ArticleRef> selectFeedSeed(@Param("userId") Long userId, @Param("excludeAuthorIds") Collection<Long> excludeAuthorIds,
                                    @Param("limit") int limit);

    List<ArticleRef> selectRecentByAuthor(@Param("authorId") Long authorId, @Param("limit") int limit);

    List<ArticleRef> selectPublishedSince(@Param("since") LocalDateTime since);

    /** 全量重建索引时按 ID 游标分批扫描已发布文章。 */
    List<ArticleDO> selectPublishedAfterId(@Param("afterId") Long afterId, @Param("limit") int limit);

    List<Long> selectIdsAfter(@Param("afterId") Long afterId, @Param("limit") int limit);

    /** 搜索降级：ES 关闭时只搜标题和摘要（LIKE '%kw%' 无法用索引，数据量大时会慢，这正是要上 ES 的原因）。 */
    IPage<ArticleDO> searchByLike(IPage<ArticleDO> page, @Param("keyword") String keyword);
}
