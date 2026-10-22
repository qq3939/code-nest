package io.github.qq3939.codenest.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 把 sourceIds 的文章关联全部并入 targetId，然后删除 sourceIds。 */
public record TagMergeReq(@NotEmpty List<Long> sourceIds, @NotNull Long targetId) {
}
