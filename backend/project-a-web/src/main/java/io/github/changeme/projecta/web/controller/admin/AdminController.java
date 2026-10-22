package io.github.changeme.projecta.web.controller.admin;

import io.github.changeme.projecta.api.common.PageResult;
import io.github.changeme.projecta.api.dto.CategorySaveReq;
import io.github.changeme.projecta.api.dto.ReasonReq;
import io.github.changeme.projecta.api.dto.SensitiveWordSaveReq;
import io.github.changeme.projecta.api.dto.StrategyUpdateReq;
import io.github.changeme.projecta.api.dto.TagMergeReq;
import io.github.changeme.projecta.api.enums.ArticleStatus;
import io.github.changeme.projecta.api.enums.DeadLetterStatus;
import io.github.changeme.projecta.api.vo.AdminUserVO;
import io.github.changeme.projecta.api.vo.ArticleListItemVO;
import io.github.changeme.projecta.api.vo.AuditLogVO;
import io.github.changeme.projecta.api.vo.CategoryVO;
import io.github.changeme.projecta.api.vo.DashboardVO;
import io.github.changeme.projecta.api.vo.DeadLetterVO;
import io.github.changeme.projecta.api.vo.SensitiveWordVO;
import io.github.changeme.projecta.api.vo.StrategyVO;
import io.github.changeme.projecta.api.vo.TagVO;
import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.core.security.UserContext;
import io.github.changeme.projecta.service.admin.AdminAuditService;
import io.github.changeme.projecta.service.admin.AdminService;
import io.github.changeme.projecta.service.admin.StrategyService;
import io.github.changeme.projecta.service.article.ArticleQueryService;
import io.github.changeme.projecta.service.article.CategoryService;
import io.github.changeme.projecta.service.article.TagService;
import io.github.changeme.projecta.service.outbox.DeadLetterService;
import io.github.changeme.projecta.service.search.SearchIndexService;
import io.github.changeme.projecta.service.sensitive.SensitiveWordService;
import io.github.changeme.projecta.service.stat.StatService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 管理端接口。/api/admin/** 的管理员校验在 AuthInterceptor 里统一做，这里不再重复。 */
@Tag(name = "管理端")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final StatService statService;
    private final AdminService adminService;
    private final AdminAuditService auditService;
    private final ArticleQueryService articleQueryService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final SensitiveWordService sensitiveWordService;
    private final DeadLetterService deadLetterService;
    private final SearchIndexService searchIndexService;
    private final StrategyService strategyService;
    private final AppProperties props;

    @Operation(summary = "数据看板：近 30 天趋势")
    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.ok(statService.dashboard());
    }

    // ---------------- 文章审核与管理 ----------------

    @Operation(summary = "文章列表：status=PENDING 即待审核列表")
    @GetMapping("/articles")
    public Result<PageResult<ArticleListItemVO>> articles(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        ArticleStatus s = status == null || status.isBlank() ? null : ArticleStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return Result.ok(articleQueryService.adminList(s, keyword, page, Math.min(size, 50)));
    }

    @Operation(summary = "审核通过")
    @PutMapping("/articles/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        adminService.approveArticle(UserContext.requireUserId(), id, reason(req));
        return Result.ok();
    }

    @Operation(summary = "审核驳回（退回草稿）")
    @PutMapping("/articles/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        adminService.rejectArticle(UserContext.requireUserId(), id, reason(req));
        return Result.ok();
    }

    @Operation(summary = "下架已发布文章")
    @PutMapping("/articles/{id}/offline")
    public Result<Void> offline(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        adminService.offlineArticle(UserContext.requireUserId(), id, reason(req));
        return Result.ok();
    }

    @Operation(summary = "恢复上架")
    @PutMapping("/articles/{id}/restore")
    public Result<Void> restore(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        adminService.restoreArticle(UserContext.requireUserId(), id, reason(req));
        return Result.ok();
    }

    // ---------------- 用户 ----------------

    @Operation(summary = "搜索用户")
    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> users(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return Result.ok(adminService.users(keyword, page, Math.min(size, 50)));
    }

    @Operation(summary = "封禁：立即生效，刷新令牌全部作废")
    @PutMapping("/users/{id}/ban")
    public Result<Void> ban(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        adminService.ban(UserContext.requireUserId(), id, reason(req));
        return Result.ok();
    }

    @Operation(summary = "解封")
    @PutMapping("/users/{id}/unban")
    public Result<Void> unban(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        adminService.unban(UserContext.requireUserId(), id, reason(req));
        return Result.ok();
    }

    // ---------------- 分类和标签 ----------------

    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.ok(categoryService.all());
    }

    @PostMapping("/categories")
    public Result<CategoryVO> createCategory(@Valid @RequestBody CategorySaveReq req) {
        CategoryVO vo = categoryService.create(req);
        adminService.log(UserContext.requireUserId(), "CATEGORY_CREATE", "CATEGORY", vo.id(), req.name());
        return Result.ok(vo);
    }

    @PutMapping("/categories/{id}")
    public Result<CategoryVO> updateCategory(@PathVariable Long id, @Valid @RequestBody CategorySaveReq req) {
        CategoryVO vo = categoryService.update(id, req);
        adminService.log(UserContext.requireUserId(), "CATEGORY_UPDATE", "CATEGORY", id, req.name());
        return Result.ok(vo);
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        adminService.log(UserContext.requireUserId(), "CATEGORY_DELETE", "CATEGORY", id, "");
        return Result.ok();
    }

    @GetMapping("/tags")
    public Result<PageResult<TagVO>> tags(@RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        return Result.ok(tagService.page(keyword, page, Math.min(size, 50)));
    }

    @Operation(summary = "合并标签：sourceIds 的文章并入 targetId，然后删除 sourceIds")
    @PostMapping("/tags/merge")
    public Result<Void> mergeTags(@Valid @RequestBody TagMergeReq req) {
        tagService.merge(req.sourceIds(), req.targetId());
        adminService.log(UserContext.requireUserId(), "TAG_MERGE", "TAG", req.targetId(), "合并 " + req.sourceIds());
        return Result.ok();
    }

    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        adminService.log(UserContext.requireUserId(), "TAG_DELETE", "TAG", id, "");
        return Result.ok();
    }

    // ---------------- 敏感词 ----------------

    @GetMapping("/sensitive-words")
    public Result<PageResult<SensitiveWordVO>> sensitiveWords(@RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "1") long page,
                                                              @RequestParam(defaultValue = "20") long size) {
        return Result.ok(sensitiveWordService.page(keyword, page, Math.min(size, 50)));
    }

    @Operation(summary = "新增敏感词，所有实例实时生效")
    @PostMapping("/sensitive-words")
    public Result<SensitiveWordVO> createWord(@Valid @RequestBody SensitiveWordSaveReq req) {
        return Result.ok(sensitiveWordService.create(req));
    }

    @PutMapping("/sensitive-words/{id}")
    public Result<SensitiveWordVO> updateWord(@PathVariable Long id, @Valid @RequestBody SensitiveWordSaveReq req) {
        return Result.ok(sensitiveWordService.update(id, req));
    }

    @DeleteMapping("/sensitive-words/{id}")
    public Result<Void> deleteWord(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        return Result.ok();
    }

    // ---------------- 死信、日志、搜索 ----------------

    @Operation(summary = "死信消息列表")
    @GetMapping("/dead-letters")
    public Result<PageResult<DeadLetterVO>> deadLetters(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        DeadLetterStatus s = status == null || status.isBlank() ? null : DeadLetterStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return Result.ok(deadLetterService.page(s, page, Math.min(size, 50)));
    }

    @Operation(summary = "重新投递死信到原队列")
    @PostMapping("/dead-letters/{id}/replay")
    public Result<Void> replay(@PathVariable Long id) {
        deadLetterService.replay(id);
        adminService.log(UserContext.requireUserId(), "DEAD_LETTER_REPLAY", "DEAD_LETTER", id, "");
        return Result.ok();
    }

    @Operation(summary = "忽略死信")
    @PostMapping("/dead-letters/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id) {
        deadLetterService.ignore(id);
        adminService.log(UserContext.requireUserId(), "DEAD_LETTER_IGNORE", "DEAD_LETTER", id, "");
        return Result.ok();
    }

    @Operation(summary = "操作日志")
    @GetMapping("/audit-logs")
    public Result<PageResult<AuditLogVO>> auditLogs(@RequestParam(required = false) String action,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return Result.ok(auditService.page(action, page, Math.min(size, 50)));
    }

    @Operation(summary = "全量重建搜索索引（ES 关闭时返回 0）")
    @PostMapping("/search/reindex")
    public Result<Map<String, Integer>> reindex() {
        int count = searchIndexService.reindexAll();
        adminService.log(UserContext.requireUserId(), "SEARCH_REINDEX", "SYSTEM", 0L, "共 " + count + " 篇");
        return Result.ok(Map.of("indexed", count));
    }

    // ---------------- 实验开关（仅 dev） ----------------

    @Operation(summary = "当前策略（仅 dev）")
    @GetMapping("/dev/strategies")
    public Result<StrategyVO> strategies() {
        requireDevTools();
        return Result.ok(strategyService.current());
    }

    @Operation(summary = "运行时切换策略（仅 dev），所有实例同步生效")
    @PutMapping("/dev/strategies")
    public Result<StrategyVO> updateStrategies(@RequestBody StrategyUpdateReq req) {
        requireDevTools();
        StrategyVO vo = strategyService.update(req);
        adminService.log(UserContext.requireUserId(), "STRATEGY_SWITCH", "SYSTEM", 0L,
                vo.countStrategy() + "/" + vo.feedMode() + "/" + vo.cacheMode());
        return Result.ok(vo);
    }

    private void requireDevTools() {
        if (!props.getDevTools().isEnabled()) {
            throw new BizException(ErrorCode.FEATURE_DISABLED);
        }
    }

    private static String reason(ReasonReq req) {
        return req == null ? null : req.reason();
    }
}
