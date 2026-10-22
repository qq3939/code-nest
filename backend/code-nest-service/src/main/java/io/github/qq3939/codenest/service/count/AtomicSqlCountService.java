package io.github.qq3939.codenest.service.count;

import io.github.qq3939.codenest.api.enums.CountStrategy;
import io.github.qq3939.codenest.api.vo.ArticleCountVO;
import io.github.qq3939.codenest.service.count.mapper.ArticleStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * UPDATE ... SET x = x + 1：读改写在数据库里一步完成，行锁保证正确。
 * 代价：每次计数都是一次数据库写，热点文章的同一行会被频繁加锁，并发高时行锁等待成为瓶颈。
 */
@Component
@RequiredArgsConstructor
public class AtomicSqlCountService implements CountService {
    private final ArticleStatMapper mapper;
    private final DbCountReader reader;

    @Override
    public CountStrategy strategy() {
        return CountStrategy.ATOMIC_SQL;
    }

    @Override
    public void increase(Long articleId, CountField field, long delta) {
        if (mapper.increase(articleId, field.key(), delta) == 0) {
            // 历史数据可能缺 article_stat 行：补一行再加，INSERT IGNORE 保证并发补行也不报错
            mapper.insertIgnore(articleId);
            mapper.increase(articleId, field.key(), delta);
        }
    }

    @Override
    public ArticleCountVO get(Long articleId) {
        return reader.get(articleId);
    }

    @Override
    public Map<Long, ArticleCountVO> batchGet(Collection<Long> articleIds) {
        return reader.batchGet(articleIds);
    }
}
