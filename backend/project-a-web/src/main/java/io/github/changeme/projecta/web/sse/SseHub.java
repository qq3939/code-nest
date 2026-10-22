package io.github.changeme.projecta.web.sse;

import io.github.changeme.projecta.core.redis.RedisKeys;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * SSE 连接管理（SPEC 9.13）。每个实例只在内存里保存"连到本机"的连接；
 * 有新通知时，消费者把 "userId:未读数" 发布到 Redis notify:push，所有实例都会收到，
 * 持有该用户连接的实例负责推送——这样用户的长连接落在哪个实例上都能收到消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseHub {
    /** 30 分钟后服务端主动结束，前端 fetch-event-source 会自动重连；避免僵尸连接无限占用。 */
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final RedisMessageListenerContainer listenerContainer;
    private final RedisKeys keys;
    private final MeterRegistry meterRegistry;
    private final Map<Long, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        listenerContainer.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            int idx = body.indexOf(':');
            push(Long.valueOf(body.substring(0, idx)), body.substring(idx + 1));
        }, new ChannelTopic(keys.channelNotifyPush()));
        Gauge.builder("app.sse.connections", connections, c -> c.values().stream().mapToInt(Set::size).sum())
                .description("当前实例持有的 SSE 连接数").register(meterRegistry);
    }

    public SseEmitter connect(Long userId, long unread) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        connections.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(userId, emitter, "unread", String.valueOf(unread));
        return emitter;
    }

    private void push(Long userId, String unread) {
        Set<SseEmitter> emitters = connections.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.forEach(e -> send(userId, e, "unread", unread));
    }

    /** 每 25 秒发一次心跳：防止 Nginx（proxy_read_timeout）和浏览器把"长时间没数据"的连接当成断开。 */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        connections.forEach((userId, emitters) -> emitters.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                remove(userId, e);
            }
        }));
    }

    public int localConnectionCount() {
        return connections.values().stream().mapToInt(Set::size).sum();
    }

    private void send(Long userId, SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = connections.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                connections.remove(userId, emitters);
            }
        }
    }
}
