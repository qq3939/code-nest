package io.github.qq3939.codenest.core.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    /** 所有 Pub/Sub 订阅共用一个监听容器（底层一条订阅连接）。 */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }

    /**
     * 只用 Redisson 做分布式锁（看门狗续期、可重入），其他 Redis 操作仍走 Spring Data Redis。
     * 不用 redisson-spring-boot-starter：它会替换掉 Spring 的 RedisConnectionFactory，两套客户端行为混在一起不好排查。
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties props) {
        Config config = new Config();
        String address = "redis://" + props.getHost() + ":" + props.getPort();
        config.useSingleServer()
                .setAddress(address)
                .setPassword(props.getPassword() == null || props.getPassword().isBlank() ? null : props.getPassword())
                .setDatabase(props.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8);
        return Redisson.create(config);
    }
}
