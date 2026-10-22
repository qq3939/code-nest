package io.github.qq3939.codenest.service.article;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.dto.CategorySaveReq;
import io.github.qq3939.codenest.api.vo.CategoryVO;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.article.entity.CategoryDO;
import io.github.qq3939.codenest.service.article.mapper.ArticleMapper;
import io.github.qq3939.codenest.service.article.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分类：数据量很小、几乎不变、每个列表都要用，所以在本机内存缓存 30 秒。
 * 修改后本实例立即失效，其他实例最多 30 秒后看到——对分类这种数据可以接受，就不再引入广播。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {
    private static final String ALL = "all";

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final Cache<String, List<CategoryVO>> local = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(1).build();

    public List<CategoryVO> all() {
        return local.get(ALL, k -> categoryMapper.selectList(Wrappers.<CategoryDO>lambdaQuery()
                        .orderByAsc(CategoryDO::getSort).orderByAsc(CategoryDO::getId))
                .stream().map(c -> new CategoryVO(c.getId(), c.getName(), c.getSort())).toList());
    }

    public Map<Long, CategoryVO> asMap() {
        return all().stream().collect(Collectors.toMap(CategoryVO::id, Function.identity()));
    }

    public void requireExists(Long categoryId) {
        if (categoryId == null || !asMap().containsKey(categoryId)) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    public CategoryVO create(CategorySaveReq req) {
        CategoryDO row = new CategoryDO();
        row.setName(req.name().trim());
        row.setSort(req.sort());
        try {
            categoryMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CATEGORY_EXISTS);
        }
        local.invalidateAll();
        return new CategoryVO(row.getId(), row.getName(), row.getSort());
    }

    public CategoryVO update(Long id, CategorySaveReq req) {
        CategoryDO row = categoryMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        row.setName(req.name().trim());
        row.setSort(req.sort());
        try {
            categoryMapper.updateById(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CATEGORY_EXISTS);
        }
        local.invalidateAll();
        return new CategoryVO(row.getId(), row.getName(), row.getSort());
    }

    /** 还有文章在用的分类不能删，否则这些文章的分类会变成悬空引用。 */
    public void delete(Long id) {
        boolean inUse = articleMapper.exists(Wrappers.<ArticleDO>lambdaQuery().eq(ArticleDO::getCategoryId, id));
        if (inUse) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该分类下还有文章，不能删除");
        }
        categoryMapper.deleteById(id);
        local.invalidateAll();
    }
}
