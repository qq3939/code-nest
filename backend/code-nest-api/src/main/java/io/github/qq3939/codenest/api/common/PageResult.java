package io.github.qq3939.codenest.api.common;

import java.util.List;
import java.util.function.Function;

/** 页码分页的返回体。 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(records.stream().map(mapper).toList(), total, page, size);
    }
}
