package io.github.qq3939.codenest.web.interceptor;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.enums.UserStatus;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.core.security.JwtUtil;
import io.github.qq3939.codenest.core.security.LoginUser;
import io.github.qq3939.codenest.core.security.UserContext;
import io.github.qq3939.codenest.core.util.IpUtils;
import io.github.qq3939.codenest.service.stat.StatService;
import io.github.qq3939.codenest.service.user.UserStatusService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 鉴权拦截器（不引入 Spring Security，见 D-005）：
 * 1. 有 Authorization 头就解析访问令牌，并查用户状态缓存，封禁立即生效；
 * 2. 没标 @Public 的接口必须登录；/api/admin/** 还必须是管理员；
 * 3. 顺便记录日活（HyperLogLog）。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {
    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserStatusService userStatusService;
    private final StatService statService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE 等异步请求结束时会再派发一次（ASYNC），那时令牌可能已过期，且鉴权在第一次派发时已做过
        if (!(handler instanceof HandlerMethod method) || request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        LoginUser user = resolveUser(request);
        if (user != null) {
            UserContext.set(user);
        }
        statService.recordVisit(user == null ? null : user.userId(), IpUtils.clientIp(request));

        boolean isPublic = method.hasMethodAnnotation(Public.class) || method.getBeanType().isAnnotationPresent(Public.class);
        if (!isPublic && user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (request.getRequestURI().startsWith("/api/admin") && (user == null || !user.isAdmin())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 异步请求（SSE）开始后 Tomcat 线程会被归还，这时不会调用 afterCompletion，必须在这里清理 ThreadLocal。 */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
    }

    private LoginUser resolveUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        // 带了令牌却无效/过期时直接 401，而不是当作匿名：前端据此自动刷新令牌再重试
        LoginUser user = jwtUtil.parse(header.substring(BEARER.length()).trim());
        UserStatus status = userStatusService.status(user.userId());
        if (status == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (status == UserStatus.BANNED) {
            throw new BizException(ErrorCode.USER_BANNED);
        }
        return user;
    }
}
