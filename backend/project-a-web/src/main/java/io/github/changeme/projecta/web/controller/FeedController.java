package io.github.changeme.projecta.web.controller;

import io.github.changeme.projecta.api.common.CursorPage;
import io.github.changeme.projecta.api.vo.ArticleListItemVO;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.core.security.UserContext;
import io.github.changeme.projecta.service.feed.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "关注动态")
@RestController
@RequiredArgsConstructor
public class FeedController {
    private final FeedService feedService;

    @Operation(summary = "关注动态（游标分页）。一页可能少于 size 条（读时过滤），以 hasMore 判断是否继续加载")
    @GetMapping("/api/feed")
    public Result<CursorPage<ArticleListItemVO>> feed(@RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false) Integer size) {
        return Result.ok(feedService.read(UserContext.requireUserId(), cursor, Pages.cursorSize(size)));
    }
}
