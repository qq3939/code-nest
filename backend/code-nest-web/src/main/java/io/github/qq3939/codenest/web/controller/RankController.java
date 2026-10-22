package io.github.qq3939.codenest.web.controller;

import io.github.qq3939.codenest.api.enums.RankPeriod;
import io.github.qq3939.codenest.api.vo.ActiveRankVO;
import io.github.qq3939.codenest.api.vo.HotArticleRankVO;
import io.github.qq3939.codenest.core.result.Result;
import io.github.qq3939.codenest.service.article.ArticleQueryService;
import io.github.qq3939.codenest.service.rank.RankService;
import io.github.qq3939.codenest.web.interceptor.Public;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@Tag(name = "排行")
@Public
@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
public class RankController {
    private final RankService rankService;
    private final ArticleQueryService articleQueryService;

    @Operation(summary = "作者活跃榜：period=day|week|month")
    @GetMapping("/active")
    public Result<List<ActiveRankVO>> active(@RequestParam(defaultValue = "day") String period,
                                             @RequestParam(defaultValue = "20") int limit) {
        RankPeriod p = RankPeriod.valueOf(period.toUpperCase(Locale.ROOT));
        return Result.ok(rankService.activeRank(p, Math.min(Math.max(limit, 1), 100)));
    }

    @Operation(summary = "热门文章榜（热度分随时间衰减）")
    @GetMapping("/hot-articles")
    public Result<List<HotArticleRankVO>> hotArticles(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(articleQueryService.hotRank(Math.min(Math.max(limit, 1), 100)));
    }
}
