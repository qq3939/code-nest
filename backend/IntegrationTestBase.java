package io.github.changeme.projecta.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.lifecycle.Startables;

import java.util.Map;
import java.util.UUID;

/**
 * 集成测试基类：MySQL、Redis、RabbitMQ 用 Testcontainers 起真实容器（不连接任何真实的外部服务）。
 * 容器是静态的、整个测试 JVM 只启动一次，多个测试类共享，节省启动时间。
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("project_a")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--default-time-zone=+08:00");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management");

    static {
        Startables.deepStart(MYSQL, REDIS, RABBIT).join();
    }

    @DynamicPropertySource
    static void containerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected ObjectMapper objectMapper;

    /** 注册一个随机用户名的新用户，返回 {id, accessToken, refreshToken}。 */
    protected TestUser newUser() {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JsonNode data = call(HttpMethod.POST, "/api/auth/register", null,
                Map.of("username", username, "password", "password123", "nickname", username)).path("data");
        return new TestUser(data.path("user").path("id").asLong(), username, data.path("accessToken").asText(),
                data.path("refreshToken").asText());
    }

    protected TestUser admin() {
        JsonNode data = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", "admin", "password", "admin-password-123")).path("data");
        return new TestUser(data.path("user").path("id").asLong(), "admin", data.path("accessToken").asText(),
                data.path("refreshToken").asText());
    }

    protected JsonNode call(HttpMethod method, String path, String token, Object body) {
        return exchange(method, path, token, body, null).getBody();
    }

    protected ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String token, Object body,
                                                Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::add);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** 新建并发布一篇文章，返回文章 ID。 */
    protected long publishArticle(TestUser author, String title, String content) {
        long id = call(HttpMethod.POST, "/api/articles", author.token(),
                Map.of("title", title, "content", content, "categoryId", 1, "tags", java.util.List.of("测试")))
                .path("data").path("id").asLong();
        String submitToken = call(HttpMethod.GET, "/api/articles/submit-token", author.token(), null)
                .path("data").path("token").asText();
        JsonNode result = exchange(HttpMethod.POST, "/api/articles/" + id + "/publish", author.token(), null,
                Map.of("Idempotency-Token", submitToken)).getBody();
        if (result.path("code").asInt() != 0) {
            throw new IllegalStateException("publish failed: " + result);
        }
        return id;
    }

    public record TestUser(long id, String username, String token, String refreshToken) {
    }
}
