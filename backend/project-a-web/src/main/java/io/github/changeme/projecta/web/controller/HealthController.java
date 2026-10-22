package io.github.changeme.projecta.web.controller;

import io.github.changeme.projecta.api.vo.HealthVO;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.web.interceptor.Public;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务层面的健康检查，逐个探测依赖，方便第一次跑起来时定位"到底哪个中间件没连上"。
 * 容器编排用的存活探针走 Actuator 的 /actuator/health。
 */
@Tag(name = "健康检查")
@Public
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final ConnectionFactory rabbitConnectionFactory;
    private final AppProperties props;

    @Operation(summary = "检查后端及依赖是否正常")
    @GetMapping
    public Result<HealthVO> health() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("mysql", probe(() -> jdbcTemplate.queryForObject("SELECT 1", Integer.class) == 1));
        components.put("redis", probe(() -> "PONG".equals(redis.execute(c -> c.ping(), true))));
        components.put("rabbitmq", probe(() -> {
            try (var conn = rabbitConnectionFactory.createConnection()) {
                return conn.isOpen();
            }
        }));
        components.put("search", props.getSearch().isEnabled() ? "ENABLED" : "DISABLED(MySQL LIKE)");
        boolean allUp = components.values().stream().noneMatch("DOWN"::equals);
        return Result.ok(new HealthVO(allUp ? "UP" : "DEGRADED", "0.1.0", components));
    }

    private static String probe(Supplier<Boolean> check) {
        try {
            return Boolean.TRUE.equals(check.get()) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
