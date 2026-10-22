package io.github.qq3939.codenest.core.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    /**
     * 雪花 ID 有 19 位，超过 JS Number 的安全整数范围（2^53），前端会丢精度，所以 Long 一律输出成字符串。
     * 只处理包装类型 Long：本项目约定 ID 用 Long，计数用基本类型 long，计数仍然以数字输出。见 pitfalls P-001。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    }
}
