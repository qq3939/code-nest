package io.github.qq3939.codenest.service.rank;

import io.github.qq3939.codenest.api.vo.ArticleCountVO;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 热度分 = (点赞×2 + 收藏×3 + 评论×4 + 阅读×0.1) / (发布至今的小时数 + 2)^1.5
 * 分子是互动的加权和，评论最"重"（成本最高）；分母让分数随时间衰减（类似 Hacker News 的排序公式），
 * +2 防止刚发布的文章分母过小、分数虚高；指数 1.5 控制衰减速度，越大新文章越占优。
 */
public final class HotScore {

    private HotScore() {
    }

    public static double compute(ArticleCountVO c, LocalDateTime publishTime, LocalDateTime now) {
        double interactions = c.like() * 2 + c.collect() * 3 + c.comment() * 4 + c.read() * 0.1;
        double hours = Math.max(Duration.between(publishTime, now).toMinutes(), 0) / 60.0;
        return interactions / Math.pow(hours + 2, 1.5);
    }
}
