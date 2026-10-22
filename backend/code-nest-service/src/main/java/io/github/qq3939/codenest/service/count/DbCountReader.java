package io.github.qq3939.codenest.service.count;

import io.github.qq3939.codenest.api.vo.ArticleCountVO;
import io.github.qq3939.codenest.service.count.entity.ArticleStatDO;
import io.github.qq3939.codenest.service.count.mapper.ArticleStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** naive 和 atomic-sql 共用的"直接读库"。 */
@Component
@RequiredArgsConstructor
public class DbCountReader {
    private final ArticleStatMapper mapper;

    public ArticleCountVO get(Long articleId) {
        return CountConverter.toVO(mapper.selectById(articleId));
    }

    public Map<Long, ArticleCountVO> batchGet(Collection<Long> articleIds) {
        Map<Long, ArticleCountVO> result = new HashMap<>();
        if (articleIds.isEmpty()) {
            return result;
        }
        for (ArticleStatDO s : mapper.selectByIds(articleIds)) {
            result.put(s.getArticleId(), CountConverter.toVO(s));
        }
        articleIds.forEach(id -> result.putIfAbsent(id, ArticleCountVO.zero()));
        return result;
    }
}
