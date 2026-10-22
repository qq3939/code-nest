package io.github.changeme.projecta.web.controller;

import io.github.changeme.projecta.api.common.CursorPage;
import io.github.changeme.projecta.api.common.PageResult;
import io.github.changeme.projecta.api.dto.ArticleSaveReq;
import io.github.changeme.projecta.api.dto.CommentCreateReq;
import io.github.changeme.projecta.api.vo.ArticleDetailVO;
import io.github.changeme.projecta.api.vo.ArticleListItemVO;
import io.github.changeme.projecta.api.vo.CommentVO;
import io.github.changeme.projecta.api.vo.SubmitTokenVO;
import io.github.changeme.projecta.core.ratelimit.Dim;
import io.github.changeme.projecta.core.ratelimit.RateLimit;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.core.security.UserContext;
import io.github.changeme.projecta.service.article.ArticleQueryService;
import io.github.changeme.projecta.service.article.ArticleService;
import io.github.changeme.projecta.service.article.SubmitTokenService;
import io.github.changeme.projecta.service.interaction.CollectService;
import io.github.changeme.projecta.service.interaction.CommentService;
import io.github.changeme.projecta.service.interaction.LikeService;
import io.github.changeme.projecta.web.interceptor.Public;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "文章、点赞收藏、评论")
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {
    private final ArticleService articleService;
    private final ArticleQueryService queryService;
    private final SubmitTokenService submitTokenService;
    private final LikeService likeService;
    private final CollectService collectService;
    private final CommentService commentService;

    @Public
    @Operation(summary = "文章列表：sort=latest|hot，可按分类、标签筛选")
    @GetMapping
    public Result<PageResult<ArticleListItemVO>> list(@RequestParam(defaultValue = "latest") String sort,
                                                      @RequestParam(required = false) Long categoryId,
                                                      @RequestParam(required = false) Long tagId,
                                                      @RequestParam(required = false) Long page,
                                                      @RequestParam(required = false) Long size) {
        return Result.ok("hot".equals(sort)
                ? queryService.hot(Pages.page(page), Pages.size(size), categoryId, tagId)
                : queryService.latest(Pages.page(page), Pages.size(size), categoryId, tagId));
    }

    @Public
    @Operation(summary = "文章详情（阅读数 +1）")
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable Long id) {
        return Result.ok(queryService.detail(id, UserContext.current()));
    }

    @Operation(summary = "编辑器回显：作者读取自己的文章（含草稿）")
    @GetMapping("/{id}/edit")
    public Result<ArticleDetailVO> forEdit(@PathVariable Long id) {
        return Result.ok(queryService.forEdit(UserContext.requireUserId(), id));
    }

    @Operation(summary = "新建草稿，返回文章 ID")
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody ArticleSaveReq req) {
        return Result.ok(Map.of("id", articleService.create(UserContext.requireUserId(), req)));
    }

    @Operation(summary = "修改文章；已发布的文章修改后命中审核词会进入待审")
    @PutMapping("/{id}")
    public Result<Map<String, String>> update(@PathVariable Long id, @Valid @RequestBody ArticleSaveReq req) {
        return Result.ok(Map.of("status", articleService.update(UserContext.requireUserId(), id, req).name()));
    }

    @Operation(summary = "自动保存草稿（编辑器每 30 秒调用一次）")
    @PutMapping("/{id}/draft")
    public Result<Void> saveDraft(@PathVariable Long id, @Valid @RequestBody ArticleSaveReq req) {
        articleService.saveDraft(UserContext.requireUserId(), id, req);
        return Result.ok();
    }

    @Operation(summary = "删除文章（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(UserContext.requireUserId(), id, false);
        return Result.ok();
    }

    @Operation(summary = "领取防重复提交令牌（10 分钟有效）")
    @GetMapping("/submit-token")
    public Result<SubmitTokenVO> submitToken() {
        return Result.ok(submitTokenService.issue(UserContext.requireUserId()));
    }

    @Operation(summary = "发布草稿；请求头 Idempotency-Token 必填；返回发布后的状态（PUBLISHED 或 PENDING）")
    @RateLimit(name = "publish", windowMillis = 60_000, limit = 3, dims = Dim.USER)
    @PostMapping("/{id}/publish")
    public Result<Map<String, String>> publish(@PathVariable Long id,
                                               @RequestHeader("Idempotency-Token") String token,
                                               @Valid @RequestBody(required = false) ArticleSaveReq req) {
        return Result.ok(Map.of("status", articleService.publish(UserContext.requireUserId(), id, req, token).name()));
    }

    @Operation(summary = "点赞文章")
    @RateLimit(name = "like", windowMillis = 1000, limit = 5, dims = Dim.USER)
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        likeService.likeArticle(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消点赞文章")
    @RateLimit(name = "like", windowMillis = 1000, limit = 5, dims = Dim.USER)
    @DeleteMapping("/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        likeService.unlikeArticle(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "收藏文章")
    @RateLimit(name = "collect", windowMillis = 1000, limit = 5, dims = Dim.USER)
    @PostMapping("/{id}/collect")
    public Result<Void> collect(@PathVariable Long id) {
        collectService.collect(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消收藏")
    @RateLimit(name = "collect", windowMillis = 1000, limit = 5, dims = Dim.USER)
    @DeleteMapping("/{id}/collect")
    public Result<Void> uncollect(@PathVariable Long id) {
        collectService.uncollect(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Public
    @Operation(summary = "评论列表（游标分页）；带 parentId 时返回该一级评论下的回复")
    @GetMapping("/{id}/comments")
    public Result<CursorPage<CommentVO>> comments(@PathVariable Long id,
                                                  @RequestParam(required = false) Long parentId,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer size) {
        Long viewer = UserContext.currentUserIdOrNull();
        return Result.ok(parentId == null
                ? commentService.listTop(id, cursor, Pages.cursorSize(size), viewer)
                : commentService.listReplies(parentId, cursor, Pages.cursorSize(size), viewer));
    }

    @Operation(summary = "发表评论或回复")
    @RateLimit(name = "comment", windowMillis = 60_000, limit = 10, dims = Dim.USER)
    @PostMapping("/{id}/comments")
    public Result<CommentVO> comment(@PathVariable Long id, @Valid @RequestBody CommentCreateReq req) {
        return Result.ok(commentService.create(UserContext.requireUserId(), id, req));
    }
}
