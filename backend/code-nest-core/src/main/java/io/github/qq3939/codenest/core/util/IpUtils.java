package io.github.qq3939.codenest.core.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {

    private IpUtils() {
    }

    /**
     * 经过 Nginx 后 remoteAddr 是 Nginx 的地址，要取 X-Real-IP / X-Forwarded-For。
     * 这两个头客户端也能伪造，所以 Nginx 配置里会覆盖它们，而不是追加。
     */
    public static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        }
        return ip;
    }
}
