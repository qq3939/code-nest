package io.github.qq3939.codenest.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 所有集成测试的基类。容器用静态字段手动启动（不用 @Container），
 * 这样多个测试类共享同一组容器和同一个 Spring 上下文，整套 IT 只启动一次中间件。
 * ES 和 MinIO 不启动：搜索走 MySQL 降级，上传接口不在集成测试范围内。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("code_nest")
            .withUsername("app")
            .withPassword("app-password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management");

    static {
        MYSQL.start();
        REDIS.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        r.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        r.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;

    protected record TestUser(long id, String username, String accessToken, String refreshToken) {
    }

    protected TestUser newUser() {
        String username = "u" + Long.toString(System.nanoTime(), 36) + SEQ.incrementAndGet();
        JsonNode data = ok(call(HttpMethod.POST, "/api/auth/register", null,
                Map.of("username", username, "password", "password-123", "nickname", username)));
        return toUser(data);
    }

    protected TestUser admin() {
        JsonNode data = ok(call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", "admin", "password", "admin-password-123")));
        return toUser(data);
    }

    protected TestUser toUser(JsonNode data) {
        return new TestUser(data.path("user").path("id").asLong(), data.path("user").path("username").asText(),
                data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    /** 发请求并返回完整响应体（Result 包装）。 */
    protected ResponseEntity<JsonNode> exchange(HttpMethod method, String path, TestUser user, Object body,
                                                Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (user != null) {
            headers.setBearerAuth(user.accessToken());
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, TestUser user, Object body) {
        return exchange(method, path, user, body, null);
    }

    protected JsonNode ok(ResponseEntity<JsonNode> resp) {
        Assertions.assertEquals(200, resp.getStatusCode().value(), () -> "unexpected response: " + resp.getBody());
        Assertions.assertEquals(0, resp.getBody().path("code").asInt(), () -> "unexpected body: " + resp.getBody());
        return resp.getBody().path("data");
    }

    protected int code(ResponseEntity<JsonNode> resp) {
        return resp.getBody() == null ? -1 : resp.getBody().path("code").asInt();
    }

    protected String submitToken(TestUser user) {
        return ok(call(HttpMethod.GET, "/api/articles/submit-token", user, null)).path("token").asText();
    }

    protected Map<String, Object> articleBody(String title, String content) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("categoryId", 1);
        body.put("tags", List.of("java", "it-" + UUID.randomUUID().toString().substring(0, 6)));
        return body;
    }

    protected long createDraft(TestUser user, String title, String content) {
        return ok(call(HttpMethod.POST, "/api/articles", user, articleBody(title, content))).path("id").asLong();
    }

    protected ResponseEntity<JsonNode> publish(TestUser user, long articleId, String token) {
        return exchange(HttpMethod.POST, "/api/articles/" + articleId + "/publish", user, null,
                Map.of("Idempotency-Token", token));
    }

    /** 建草稿并发布，返回文章 ID。 */
    protected long publishArticle(TestUser user, String title) {
        long id = createDraft(user, title, "这是一篇集成测试文章的正文，" + title + "，内容足够长用于生成摘要。");
        JsonNode data = ok(publish(user, id, submitToken(user)));
        Assertions.assertEquals("PUBLISHED", data.path("status").asText());
        return id;
    }
}
