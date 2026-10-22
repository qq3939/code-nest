package io.github.qq3939.codenest.core.security;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.enums.UserRole;
import io.github.qq3939.codenest.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {
    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijklmnop";

    @Test
    void createAndParse() {
        JwtUtil jwt = new JwtUtil(SECRET, Duration.ofMinutes(30), Clock.systemUTC());
        LoginUser user = jwt.parse(jwt.createAccessToken(42L, UserRole.ADMIN));
        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void expiredTokenIsDistinguishedFromInvalid() {
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        String token = new JwtUtil(SECRET, Duration.ofMinutes(30), Clock.fixed(issued, ZoneId.of("UTC")))
                .createAccessToken(1L, UserRole.USER);
        JwtUtil later = new JwtUtil(SECRET, Duration.ofMinutes(30),
                Clock.fixed(issued.plus(Duration.ofMinutes(31)), ZoneId.of("UTC")));
        assertThatThrownBy(() -> later.parse(token))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void tamperedTokenIsUnauthorized() {
        JwtUtil jwt = new JwtUtil(SECRET, Duration.ofMinutes(30), Clock.systemUTC());
        JwtUtil other = new JwtUtil(SECRET + "x", Duration.ofMinutes(30), Clock.systemUTC());
        String token = other.createAccessToken(1L, UserRole.USER);
        assertThatThrownBy(() -> jwt.parse(token))
                .extracting(e -> ((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThatThrownBy(() -> jwt.parse("not-a-jwt"))
                .extracting(e -> ((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtUtil("short", Duration.ofMinutes(1), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
