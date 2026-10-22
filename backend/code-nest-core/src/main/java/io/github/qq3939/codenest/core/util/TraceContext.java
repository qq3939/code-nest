package io.github.qq3939.codenest.core.util;

import org.slf4j.MDC;

/** traceId 放在 MDC 里，日志 pattern 用 %X{traceId} 打出来；异步线程需要手动传递。 */
public final class TraceContext {
    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceContext() {
    }

    public static String get() {
        return MDC.get(MDC_KEY);
    }
}
