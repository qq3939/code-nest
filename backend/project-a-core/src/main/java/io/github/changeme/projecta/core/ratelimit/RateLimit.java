package io.github.changeme.projecta.core.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流。window/limit 是默认值，可以被 app.rate-limit.&lt;name&gt; 覆盖，压测时不用改代码。
 * 例：@RateLimit(name = "like", windowMillis = 1000, limit = 5, dims = Dim.USER)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String name();

    long windowMillis();

    int limit();

    Dim[] dims() default {Dim.USER, Dim.IP};
}
