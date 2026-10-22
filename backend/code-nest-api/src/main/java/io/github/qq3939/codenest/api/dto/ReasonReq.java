package io.github.qq3939.codenest.api.dto;

import jakarta.validation.constraints.Size;

/** 管理端审核、封禁、下架等操作附带的原因，写进操作日志。 */
public record ReasonReq(@Size(max = 255) String reason) {
}
