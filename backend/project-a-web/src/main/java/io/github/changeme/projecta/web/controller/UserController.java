package io.github.changeme.projecta.web.controller;

import io.github.changeme.projecta.api.common.PageResult;
import io.github.changeme.projecta.api.dto.ChangePasswordReq;
import io.github.changeme.projecta.api.dto.UpdateProfileReq;
import io.github.changeme.projecta.api.vo.ArticleListItemVO;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.api.vo.UserProfileVO;
import io.github.changeme.projecta.api.vo.UserVO;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.core.security.UserContext;
import io.github.changeme.projecta.service.article.ArticleQueryService;
import io.github.changeme.projecta.service.follow.FollowService;
import io.github.changeme.projecta.service.user.UserService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户与关注")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final FollowService followService;
    private final ArticleQueryService articleQueryService;

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.me(UserContext.requireUserId()));
    }

    @Operation(summary = "修改资料（昵称、简介、头像）")
    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileReq req) {
        return Result.ok(userService.updateProfile(UserContext.requireUserId(), req));
    }

    @Operation(summary = "修改密码，成功后其他设备需要重新登录")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        userService.changePassword(UserContext.requireUserId(), req);
        return Result.ok();
    }

    @Public
    @Operation(summary = "个人主页：资料和统计")
    @GetMapping("/{id}")
    public Result<UserProfileVO> profile(@PathVariable Long id) {
        Long viewer = UserContext.currentUserIdOrNull();
        return Result.ok(userService.profile(id, followService.isFollowing(viewer, id)));
    }

    @Public
    @Operation(summary = "个人主页：文章（本人可见草稿等全部状态）")
    @GetMapping("/{id}/articles")
    public Result<PageResult<ArticleListItemVO>> articles(@PathVariable Long id, @RequestParam(required = false) Long page,
                                                          @RequestParam(required = false) Long size) {
        return Result.ok(articleQueryService.byAuthor(id, UserContext.currentUserIdOrNull(), Pages.page(page), Pages.size(size)));
    }

    @Public
    @Operation(summary = "个人主页：收藏")
    @GetMapping("/{id}/collections")
    public Result<PageResult<ArticleListItemVO>> collections(@PathVariable Long id, @RequestParam(required = false) Long page,
                                                             @RequestParam(required = false) Long size) {
        return Result.ok(articleQueryService.collections(id, Pages.page(page), Pages.size(size)));
    }

    @Public
    @Operation(summary = "粉丝列表")
    @GetMapping("/{id}/followers")
    public Result<PageResult<UserBriefVO>> followers(@PathVariable Long id, @RequestParam(required = false) Long page,
                                                     @RequestParam(required = false) Long size) {
        return Result.ok(followService.followers(id, Pages.page(page), Pages.size(size)));
    }

    @Public
    @Operation(summary = "关注列表")
    @GetMapping("/{id}/followings")
    public Result<PageResult<UserBriefVO>> followings(@PathVariable Long id, @RequestParam(required = false) Long page,
                                                      @RequestParam(required = false) Long size) {
        return Result.ok(followService.followings(id, Pages.page(page), Pages.size(size)));
    }

    @Operation(summary = "关注")
    @PostMapping("/{id}/follow")
    public Result<Void> follow(@PathVariable Long id) {
        followService.follow(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/{id}/follow")
    public Result<Void> unfollow(@PathVariable Long id) {
        followService.unfollow(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
