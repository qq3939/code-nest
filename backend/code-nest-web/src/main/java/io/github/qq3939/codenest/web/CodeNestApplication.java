package io.github.qq3939.codenest.web;

import io.github.qq3939.codenest.core.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
@SpringBootApplication(scanBasePackages = "io.github.qq3939.codenest")
public class CodeNestApplication {

    public static void main(String[] args) {
        // JVM 默认时区跟随宿主机；容器里通常是 UTC，统一成上海，避免"今天"的日榜 key 和数据库时间对不上
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(CodeNestApplication.class, args);
    }
}
