package io.github.changeme.projecta.web.controller;

import io.github.changeme.projecta.core.ratelimit.Dim;
import io.github.changeme.projecta.core.ratelimit.RateLimit;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.core.security.UserContext;
import io.github.changeme.projecta.service.interaction.CommentService;
import io.github.changeme.projecta.service.interaction.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文章、点赞收藏、评论")
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;
    private final LikeService likeService;

    @Operation(summary = "删除自己的评论（一级评论连同回复一起删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(UserContext.requireUserId(), id, false);
        return Result.ok();
    }

    @Operation(summary = "点赞评论")
    @RateLimit(name = "like", windowMillis = 1000, limit = 5, dims = Dim.USER)
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        likeService.likeComment(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消点赞评论")
    @RateLimit(name = "like", windowMillis = 1000, limit = 5, dims = Dim.USER)
    @DeleteMapping("/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        likeService.unlikeComment(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
