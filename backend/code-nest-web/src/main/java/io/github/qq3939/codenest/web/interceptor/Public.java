package io.github.qq3939.codenest.web.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 Controller 方法或类上，表示未登录也能访问。默认所有接口都要求登录——
 * "默认拒绝、显式放行"比反过来安全：新加接口忘了标注，最坏结果是多要求了一次登录，而不是泄露数据。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Public {
}
