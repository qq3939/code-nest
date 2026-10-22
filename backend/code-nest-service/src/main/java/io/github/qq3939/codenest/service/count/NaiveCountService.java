package io.github.qq3939.codenest.service.count;

import io.github.qq3939.codenest.api.enums.CountStrategy;
import io.github.qq3939.codenest.api.vo.ArticleCountVO;
import io.github.qq3939.codenest.service.count.entity.ArticleStatDO;
import io.github.qq3939.codenest.service.count.mapper.ArticleStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 【对照组，故意写错】先读出整行、在 Java 里加 1、再整行写回。
 * 两个线程同时读到 10，各自加 1 写回 11，结果少加了一次——这就是"丢失更新"。
 * 集成测试 CountConcurrencyIT 用 200 线程复现：最终值明显小于 1000。不要把它设为默认策略。
 */
@Component
@RequiredArgsConstructor
public class NaiveCountService implements CountService {
    private final ArticleStatMapper mapper;
    private final DbCountReader reader;

    @Override
    public CountStrategy strategy() {
        return CountStrategy.NAIVE;
    }

    @Override
    public void increase(Long articleId, CountField field, long delta) {
        ArticleStatDO stat = mapper.selectById(articleId);
        if (stat == null) {
            mapper.insertIgnore(articleId);
            stat = mapper.selectById(articleId);
        }
        switch (field) {
            case READ -> stat.setReadCount(Math.max(stat.getReadCount() + delta, 0));
            case LIKE -> stat.setLikeCount(Math.max(stat.getLikeCount() + delta, 0));
            case COLLECT -> stat.setCollectCount(Math.max(stat.getCollectCount() + delta, 0));
            case COMMENT -> stat.setCommentCount(Math.max(stat.getCommentCount() + delta, 0));
        }
        mapper.updateById(stat);
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
