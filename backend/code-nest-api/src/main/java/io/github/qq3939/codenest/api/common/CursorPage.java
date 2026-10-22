package io.github.qq3939.codenest.api.common;

import java.util.List;

/**
 * 游标分页的返回体。Feed、评论、通知这类"不断有新数据插到头部"的列表用游标，
 * 页码分页在这种场景下会因为前面插入新数据而出现重复或遗漏。
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
