package io.github.qq3939.codenest.core.security;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.core.exception.BizException;

/**
 * 当前请求的登录用户。拦截器在 preHandle 里放入，在 afterCompletion 里清除——
 * Tomcat 线程是复用的，不清除就会把上一个请求的用户"借"给下一个请求。
 */
public final class UserContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 可能为空：公开接口在未登录时也能访问。 */
    public static LoginUser current() {
        return HOLDER.get();
    }

    public static Long currentUserIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user.userId();
    }
}
