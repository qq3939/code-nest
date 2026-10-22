package io.github.changeme.projecta.web.handler;

import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.core.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 把各种异常统一翻译成 Result。原则：业务异常原样返回提示；参数异常返回字段级提示；
 * 未知异常只返回"系统繁忙"和 traceId，堆栈只进日志，不暴露给前端（SPEC 9.15）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return build(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return build(ErrorCode.PARAM_INVALID, message.isEmpty() ? ErrorCode.PARAM_INVALID.message() : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        return build(ErrorCode.PARAM_INVALID, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        log.debug("bad request: {}", e.getMessage());
        return build(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.message());
    }

    /** 例如枚举参数取值不合法（period=year）。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.debug("illegal argument: {}", e.getMessage());
        return build(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.message());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return build(ErrorCode.FILE_INVALID, "文件不能超过 5 MB");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.message());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(Result.fail(ErrorCode.PARAM_INVALID, "不支持的请求方法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("unhandled exception", e);
        return build(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.message());
    }

    private static ResponseEntity<Result<Void>> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.httpStatus()).body(Result.fail(code, message));
    }
}
