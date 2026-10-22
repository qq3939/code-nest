package io.github.qq3939.codenest.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.qq3939.codenest.api.enums.UserRole;
import io.github.qq3939.codenest.core.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 注册登录、令牌轮换、登录失败锁定、封禁、权限（SPEC M1 验收）。 */
class AuthIT extends IntegrationTestBase {

    @Test
    void registerLoginAndMe() {
        TestUser u = newUser();
        // Long 序列化成字符串，前端不会丢精度（P-001）
        ResponseEntity<JsonNode> me = call(HttpMethod.GET, "/api/users/me", u, null);
        assertThat(me.getBody().path("data").path("id").isTextual()).isTrue();
        assertThat(ok(me).path("username").asText()).isEqualTo(u.username());

        ResponseEntity<JsonNode> dup = call(HttpMethod.POST, "/api/auth/register", null,
                Map.of("username", u.username(), "password", "password-123"));
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(code(dup)).isEqualTo(20001);
    }

    @Test
    void requestWithoutTokenIs401() {
        ResponseEntity<JsonNode> resp = call(HttpMethod.GET, "/api/users/me", null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        // 公开接口不需要登录
        assertThat(call(HttpMethod.GET, "/api/categories", null, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void expiredAccessTokenIsRejectedWithRefreshableCode() {
        TestUser u = newUser();
        // 用同一个密钥、但把时钟拨回 1 小时前签发，令牌（30 分钟有效）此刻已经过期
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneId.of("Asia/Shanghai"));
        JwtUtil oldClock = new JwtUtil("integration-test-secret-0123456789-abcdefghijklmn", Duration.ofMinutes(30), past);
        TestUser expired = new TestUser(u.id(), u.username(), oldClock.createAccessToken(u.id(), UserRole.USER), u.refreshToken());

        ResponseEntity<JsonNode> resp = call(HttpMethod.GET, "/api/users/me", expired, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        // 20005 和"未登录"10002 区分开：前端据此决定是用 refresh token 换新令牌，还是跳登录页
        assertThat(code(resp)).isEqualTo(20005);
    }

    @Test
    void refreshTokenRotatesAndOldOneIsInvalid() {
        TestUser u = newUser();
        JsonNode refreshed = ok(call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", u.refreshToken())));
        assertThat(refreshed.path("refreshToken").asText()).isNotEqualTo(u.refreshToken());

        ResponseEntity<JsonNode> reuse = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", u.refreshToken()));
        assertThat(code(reuse)).isEqualTo(20006);

        ok(call(HttpMethod.POST, "/api/auth/logout", null, Map.of("refreshToken", refreshed.path("refreshToken").asText())));
        ResponseEntity<JsonNode> afterLogout = call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", refreshed.path("refreshToken").asText()));
        assertThat(code(afterLogout)).isEqualTo(20006);
    }

    @Test
    void accountLockedAfterFiveFailures() {
        TestUser u = newUser();
        for (int i = 0; i < 5; i++) {
            ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/auth/login", null,
                    Map.of("username", u.username(), "password", "wrong-password"));
            assertThat(code(r)).isEqualTo(20002);
        }
        // 第 6 次即使密码正确也被锁
        ResponseEntity<JsonNode> locked = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", u.username(), "password", "password-123"));
        assertThat(code(locked)).isEqualTo(20003);
    }

    @Test
    void adminEndpointsRequireAdminAndBanTakesEffect() {
        TestUser u = newUser();
        assertThat(call(HttpMethod.GET, "/api/admin/dashboard", u, null).getStatusCode().value()).isEqualTo(403);

        TestUser admin = admin();
        ok(call(HttpMethod.GET, "/api/admin/dashboard", admin, null));
        ok(call(HttpMethod.PUT, "/api/admin/users/" + u.id() + "/ban", admin, Map.of("reason", "测试封禁")));

        // 已签发的 access token 立刻失效（状态缓存在封禁时被清掉）
        ResponseEntity<JsonNode> me = call(HttpMethod.GET, "/api/users/me", u, null);
        assertThat(code(me)).isEqualTo(20004);
        ResponseEntity<JsonNode> login = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", u.username(), "password", "password-123"));
        assertThat(code(login)).isEqualTo(20004);

        ok(call(HttpMethod.PUT, "/api/admin/users/" + u.id() + "/unban", admin, null));
        ok(call(HttpMethod.GET, "/api/users/me", u, null));

        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_log WHERE target_id = ?", Integer.class, u.id());
        assertThat(audits).isGreaterThanOrEqualTo(2);
    }
}
