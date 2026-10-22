package io.github.changeme.projecta.service.article;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.api.common.PageResult;
import io.github.changeme.projecta.api.vo.TagVO;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.service.article.entity.ArticleTagDO;
import io.github.changeme.projecta.service.article.entity.TagDO;
import io.github.changeme.projecta.service.article.mapper.ArticleTagMapper;
import io.github.changeme.projecta.service.article.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    public List<TagVO> search(String keyword, int limit) {
        return tagMapper.selectList(Wrappers.<TagDO>lambdaQuery()
                        .likeRight(keyword != null && !keyword.isBlank(), TagDO::getName, keyword)
                        .orderByAsc(TagDO::getName)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 50)))
                .stream().map(t -> new TagVO(t.getId(), t.getName())).toList();
    }

    public PageResult<TagVO> page(String keyword, long page, long size) {
        Page<TagDO> result = tagMapper.selectPage(Page.of(page, size), Wrappers.<TagDO>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), TagDO::getName, keyword)
                .orderByDesc(TagDO::getId));
        return new PageResult<>(result.getRecords().stream().map(t -> new TagVO(t.getId(), t.getName())).toList(),
                result.getTotal(), page, size);
    }

    /** 按名字取标签 ID，不存在就新建（"标签多选，可新建"）。 */
    public List<Long> ensureTags(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        names.stream().filter(n -> n != null && !n.isBlank()).map(String::trim).forEach(cleaned::add);
        if (cleaned.isEmpty()) {
            return List.of();
        }
        for (String name : cleaned) {
            TagDO tag = new TagDO();
            tag.setId(IdWorker.getId());
            tag.setName(name);
            tagMapper.insertIgnore(tag);
        }
        return tagMapper.selectList(Wrappers.<TagDO>lambdaQuery().in(TagDO::getName, cleaned))
                .stream().map(TagDO::getId).toList();
    }

    public void replaceArticleTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(Wrappers.<ArticleTagDO>lambdaQuery().eq(ArticleTagDO::getArticleId, articleId));
        if (!tagIds.isEmpty()) {
            articleTagMapper.insertBatchIgnore(articleId, tagIds);
        }
    }

    public Map<Long, List<TagVO>> tagsOf(Collection<Long> articleIds) {
        Map<Long, List<TagVO>> result = new HashMap<>();
        if (articleIds.isEmpty()) {
            return result;
        }
        for (ArticleTagRow row : tagMapper.selectByArticleIds(articleIds)) {
            result.computeIfAbsent(row.articleId(), k -> new ArrayList<>()).add(new TagVO(row.tagId(), row.tagName()));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void merge(List<Long> sourceIds, Long targetId) {
        if (tagMapper.selectById(targetId) == null) {
            throw new BizException(ErrorCode.TAG_NOT_FOUND);
        }
        for (Long sourceId : sourceIds) {
            if (sourceId.equals(targetId)) {
                continue;
            }
            articleTagMapper.moveTag(sourceId, targetId);
            // UPDATE IGNORE 跳过的行（文章同时挂了 source 和 target）还留着 source，一并删掉
            articleTagMapper.delete(Wrappers.<ArticleTagDO>lambdaQuery().eq(ArticleTagDO::getTagId, sourceId));
            tagMapper.deleteById(sourceId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long tagId) {
        articleTagMapper.delete(Wrappers.<ArticleTagDO>lambdaQuery().eq(ArticleTagDO::getTagId, tagId));
        tagMapper.deleteById(tagId);
    }
}
