package io.github.changeme.projecta.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.changeme.projecta.api.enums.UserRole;
import io.github.changeme.projecta.core.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** SPEC 13.2 鉴权：令牌过期、刷新令牌轮换、封禁即时生效；另外覆盖登录失败锁定。 */
class AuthIT extends IntegrationTestBase {

    @Test
    void protectedApiRequiresLogin() {
        ResponseEntity<JsonNode> resp = exchange(HttpMethod.GET, "/api/users/me", null, null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        assertThat(resp.getBody().path("code").asInt()).isEqualTo(10002);
        assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
        assertThat(resp.getBody().path("traceId").asText()).isEqualTo(resp.getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void expiredAccessTokenReturnsTokenExpired() {
        TestUser user = newUser();
        Instant longAgo = Instant.now().minus(Duration.ofHours(2));
        String expired = new JwtUtil("integration-test-secret-0123456789-abcdefghijklmn", Duration.ofMinutes(30),
                Clock.fixed(longAgo, ZoneId.systemDefault())).createAccessToken(user.id(), UserRole.USER);
        ResponseEntity<JsonNode> resp = exchange(HttpMethod.GET, "/api/users/me", expired, null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        assertThat(resp.getBody().path("code").asInt()).isEqualTo(20005);
    }

    @Test
    void refreshTokenIsRotatedAndOldOneIsRevoked() {
        TestUser user = newUser();
        JsonNode first = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken()));
        assertThat(first.path("code").asInt()).isZero();
        String newRefresh = first.path("data").path("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(user.refreshToken());

        JsonNode reuse = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken()));
        assertThat(reuse.path("code").asInt()).isEqualTo(20006);

        call(HttpMethod.POST, "/api/auth/logout", null, Map.of("refreshToken", newRefresh));
        JsonNode afterLogout = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", newRefresh));
        assertThat(afterLogout.path("code").asInt()).isEqualTo(20006);
    }

    @Test
    void banTakesEffectImmediately() {
        TestUser user = newUser();
        TestUser admin = admin();
        assertThat(call(HttpMethod.GET, "/api/users/me", user.token(), null).path("code").asInt()).isZero();

        JsonNode ban = call(HttpMethod.PUT, "/api/admin/users/" + user.id() + "/ban", admin.token(), Map.of("reason", "test"));
        assertThat(ban.path("code").asInt()).isZero();

        // 访问令牌还没过期，但下一个请求就被拦住；刷新令牌也已作废
        ResponseEntity<JsonNode> resp = exchange(HttpMethod.GET, "/api/users/me", user.token(), null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody().path("code").asInt()).isEqualTo(20004);
        assertThat(call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", user.refreshToken()))
                .path("code").asInt()).isEqualTo(20006);

        call(HttpMethod.PUT, "/api/admin/users/" + user.id() + "/unban", admin.token(), Map.of());
        assertThat(call(HttpMethod.GET, "/api/users/me", user.token(), null).path("code").asInt()).isZero();
    }

    @Test
    void nonAdminCannotAccessAdminApi() {
        ResponseEntity<JsonNode> resp = exchange(HttpMethod.GET, "/api/admin/dashboard", newUser().token(), null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void accountLocksAfterFiveFailures() {
        TestUser user = newUser();
        for (int i = 0; i < 5; i++) {
            JsonNode r = call(HttpMethod.POST, "/api/auth/login", null, Map.of("username", user.username(), "password", "wrong-pass"));
            assertThat(r.path("code").asInt()).isEqualTo(20002);
        }
        JsonNode locked = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", user.username(), "password", "password123"));
        assertThat(locked.path("code").asInt()).isEqualTo(20003);
    }
}
