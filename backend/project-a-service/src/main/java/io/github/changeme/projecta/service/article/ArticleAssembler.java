package io.github.changeme.projecta.service.article;

import io.github.changeme.projecta.api.vo.ArticleCountVO;
import io.github.changeme.projecta.api.vo.ArticleListItemVO;
import io.github.changeme.projecta.api.vo.CategoryVO;
import io.github.changeme.projecta.api.vo.TagVO;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.service.article.entity.ArticleDO;
import io.github.changeme.projecta.service.count.CountService;
import io.github.changeme.projecta.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 把一页 ArticleDO 组装成列表项：分类、标签、作者、计数各批量查一次，总共 4 次查询，与页大小无关。
 * 如果逐条去查就是经典的 N+1 问题：20 条数据要 1 + 20×4 次查询。
 */
@Component
@RequiredArgsConstructor
public class ArticleAssembler {
    private final CategoryService categoryService;
    private final TagService tagService;
    private final UserService userService;
    private final CountService countService;

    public List<ArticleListItemVO> toListItems(List<ArticleDO> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, CategoryVO> categories = categoryService.asMap();
        Map<Long, List<TagVO>> tags = tagService.tagsOf(ids);
        Map<Long, UserBriefVO> authors = userService.briefs(articles.stream().map(ArticleDO::getAuthorId).toList());
        Map<Long, ArticleCountVO> counts = countService.batchGet(ids);
        return articles.stream().map(a -> new ArticleListItemVO(a.getId(), a.getTitle(), a.getSummary(),
                a.getCoverUrl(), categories.get(a.getCategoryId()), tags.getOrDefault(a.getId(), List.of()),
                authors.get(a.getAuthorId()), a.getStatus().name(), a.getPublishTime(), a.getUpdateTime(),
                counts.getOrDefault(a.getId(), ArticleCountVO.zero()))).toList();
    }
}
