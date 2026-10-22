package io.github.qq3939.codenest.api.dto;

import java.util.List;

/** all=true 时忽略 ids，全部标记已读。 */
public record MarkReadReq(List<Long> ids, boolean all) {
}
