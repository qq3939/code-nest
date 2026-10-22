package io.github.changeme.projecta.core.exception;

import io.github.changeme.projecta.api.common.ErrorCode;
import lombok.Getter;

/** 业务异常。只携带错误码和面向用户的提示，不填充堆栈，因为它是预期内的分支而不是故障。 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message, null, false, false);
        this.errorCode = errorCode;
    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throw new BizException(errorCode);
        }
    }
}
