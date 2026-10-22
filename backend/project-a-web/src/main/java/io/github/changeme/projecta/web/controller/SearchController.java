package io.github.changeme.projecta.web.controller;

import io.github.changeme.projecta.api.common.PageResult;
import io.github.changeme.projecta.api.vo.SearchHitVO;
import io.github.changeme.projecta.core.ratelimit.Dim;
import io.github.changeme.projecta.core.ratelimit.RateLimit;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.service.search.SearchService;
import io.github.changeme.projecta.web.interceptor.Public;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "搜索")
@Public
@RestController
@RequiredArgsConstructor
public class SearchController {
    private final SearchService searchService;

    @Operation(summary = "全文搜索，结果里 <em> 包裹的是高亮关键词；ES 关闭时退回 MySQL LIKE")
    @RateLimit(name = "search", windowMillis = 1000, limit = 10, dims = Dim.IP)
    @GetMapping("/api/search")
    public Result<PageResult<SearchHitVO>> search(@RequestParam("q") String q,
                                                  @RequestParam(required = false) Long page,
                                                  @RequestParam(required = false) Long size) {
        return Result.ok(searchService.search(q, Pages.page(page), Pages.size(size)));
    }
}
