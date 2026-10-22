package io.github.changeme.projecta.api.dto;

import java.util.List;

/** all=true 时忽略 ids，全部标记已读。 */
public record MarkReadReq(List<Long> ids, boolean all) {
}
