package io.github.changeme.projecta.core.security;

import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.api.enums.UserRole;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * 访问令牌：HS256，带 userId（sub）、role、jti。
 * 不是 Spring Bean 的普通类，方便在单元测试里直接 new 出来、注入固定的 Clock 测过期。
 */
public class JwtUtil {
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtUtil(String secret, Duration ttl, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 至少 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.clock = clock;
    }

    public static JwtUtil from(AppProperties props) {
        return new JwtUtil(props.getJwt().getSecret(), props.getJwt().getAccessTtl(), Clock.systemDefaultZone());
    }

    public String createAccessToken(Long userId, UserRole role) {
        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 过期和其他无效（签名错、格式错）区分开：前者前端应当去刷新，后者直接重新登录。 */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(Long.valueOf(claims.getSubject()), UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
