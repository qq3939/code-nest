package io.github.changeme.projecta.service.count;

import io.github.changeme.projecta.api.enums.CountStrategy;
import io.github.changeme.projecta.api.vo.ArticleCountVO;

import java.util.Collection;
import java.util.Map;

/**
 * 文章计数（SPEC 9.2）。三种实现：naive（对照组，故意有并发覆盖问题）、atomic-sql、redis-batch（默认）。
 * 业务代码只依赖这个接口，由 RoutingCountService 按当前策略分发，切换策略不用改业务代码。
 */
public interface CountService {

    CountStrategy strategy();

    void increase(Long articleId, CountField field, long delta);

    ArticleCountVO get(Long articleId);

    Map<Long, ArticleCountVO> batchGet(Collection<Long> articleIds);
}
