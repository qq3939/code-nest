package io.github.qq3939.codenest.service.feed;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.core.exception.BizException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Feed 游标 = (上一页最小的时间戳, 这个时间戳已经返回了几条)。
 * 只记时间戳不够：同一毫秒可能有多篇文章，翻页时会重复或遗漏；多记一个 offset 就能精确接上。
 */
public record FeedCursor(long maxScore, int offset) {

    public static final FeedCursor FIRST = new FeedCursor(Long.MAX_VALUE, 0);

    public boolean isFirst() {
        return maxScore == Long.MAX_VALUE;
    }

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((maxScore + ":" + offset).getBytes(StandardCharsets.UTF_8));
    }

    public static FeedCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return FIRST;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":");
            return new FeedCursor(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }
}
