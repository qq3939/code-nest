package io.github.changeme.projecta.core.ratelimit;

import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.core.security.UserContext;
import io.github.changeme.projecta.core.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {
    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final AppProperties props;
    private final DefaultRedisScript<Long> script;

    public RateLimitAspect(StringRedisTemplate redis, RedisKeys keys, AppProperties props) {
        this.redis = redis;
        this.keys = keys;
        this.props = props;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/sliding_window.lua"));
        this.script.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        long window = rateLimit.windowMillis();
        int limit = rateLimit.limit();
        AppProperties.RateLimitRule override = props.getRateLimit().get(rateLimit.name());
        if (override != null) {
            window = override.getWindow().toMillis();
            limit = override.getLimit();
        }
        List<String> limitKeys = RateLimitKeyResolver.resolve(keys, rateLimit.name(), rateLimit.dims(),
                UserContext.currentUserIdOrNull(), currentIp());
        Long allowed;
        try {
            allowed = redis.execute(script, limitKeys, String.valueOf(System.currentTimeMillis()),
                    String.valueOf(window), String.valueOf(limit), UUID.randomUUID().toString());
        } catch (Exception e) {
            // 限流是保护措施，不是业务本身：Redis 故障时放行，而不是让整个接口不可用
            log.warn("rate limit check failed, allow request", e);
            return pjp.proceed();
        }
        if (allowed == null || allowed == 0L) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return pjp.proceed();
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return IpUtils.clientIp(request);
        }
        return "unknown";
    }
}
