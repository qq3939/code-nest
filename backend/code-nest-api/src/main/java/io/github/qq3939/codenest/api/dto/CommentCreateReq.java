package io.github.qq3939.codenest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * replyCommentId 为空表示一级评论；否则是回复某条评论（可以是一级评论，也可以是楼中楼），
 * 服务端会把它归到所属的一级评论下，只保留两级。
 */
public record CommentCreateReq(@NotBlank @Size(max = 1000) String content, Long replyCommentId) {
}
