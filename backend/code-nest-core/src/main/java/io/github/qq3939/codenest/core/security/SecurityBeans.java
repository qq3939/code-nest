package io.github.qq3939.codenest.core.security;

import io.github.qq3939.codenest.core.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityBeans {

    @Bean
    public JwtUtil jwtUtil(AppProperties props) {
        return JwtUtil.from(props);
    }

    /** BCrypt 自带随机盐，同一个密码每次哈希结果都不同；强度 10 约 60–100ms，兼顾安全和登录耗时。 */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
