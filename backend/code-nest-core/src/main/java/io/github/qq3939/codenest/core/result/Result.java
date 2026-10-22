package io.github.qq3939.codenest.core.result;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.core.util.TraceContext;

/**
 * 统一返回体。traceId 让用户反馈问题时能直接贴出来，后端按它在日志里查整条链路。
 */
public record Result<T>(int code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data, TraceContext.get());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.message());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null, TraceContext.get());
    }
}
