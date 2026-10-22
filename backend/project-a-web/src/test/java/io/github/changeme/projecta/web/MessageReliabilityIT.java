package io.github.changeme.projecta.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.changeme.projecta.api.event.EventMessage;
import io.github.changeme.projecta.api.event.EventTypes;
import io.github.changeme.projecta.api.event.Payloads;
import io.github.changeme.projecta.core.mq.MqNames;
import io.github.changeme.projecta.service.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 消息可靠性（SPEC 9.5 验收）：消费幂等、多级重试后进死信、死信重放、本地消息表补偿投递。 */
class MessageReliabilityIT extends IntegrationTestBase {

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    MqNames mqNames;
    @Autowired
    OutboxPublisher outboxPublisher;

    @Test
    void duplicateMessageCreatesOnlyOneNotification() throws Exception {
        TestUser u = newUser();
        String msgId = UUID.randomUUID().toString();
        Message m = message(msgId, EventTypes.SYSTEM_NOTICE, new Payloads.SystemNoticeEvent(u.id(), "系统通知：重复投递测试"));
        rabbitTemplate.send(mqNames.eventExchange(), EventTypes.SYSTEM_NOTICE, m);
        rabbitTemplate.send(mqNames.eventExchange(), EventTypes.SYSTEM_NOTICE, m);

        await().atMost(Duration.ofSeconds(10)).until(() -> countNotify(msgId) == 1);
        // 再等一会，确认第二条消息被当作重复消息丢掉，而不是还没消费
        Thread.sleep(1000);
        assertThat(countNotify(msgId)).isEqualTo(1);
    }

    @Test
    void failingMessageGoesToDeadLetterAndCanBeReplayed() throws Exception {
        String msgId = UUID.randomUUID().toString();
        // articleId 不是数字，反序列化 payload 必然失败：模拟一条"怎么重试都不会成功"的消息
        ObjectNode bad = objectMapper.createObjectNode().put("userId", 1).put("targetType", "ARTICLE")
                .put("targetId", "not-a-number").put("ownerId", 2).put("articleId", "not-a-number");
        rabbitTemplate.send(mqNames.eventExchange(), EventTypes.INTERACTION_LIKED,
                message(msgId, EventTypes.INTERACTION_LIKED, bad));

        // 重试 3 次（测试配置里每次 200ms）后进入 notify.queue.dlq，再由死信消费者落库
        await().atMost(Duration.ofSeconds(20)).until(() -> countDeadLetter(msgId, MqNames.NOTIFY_QUEUE) == 1);
        Long dlId = jdbc.queryForObject("SELECT id FROM dead_letter_message WHERE msg_id = ? AND queue = ?",
                Long.class, msgId, MqNames.NOTIFY_QUEUE);

        TestUser admin = admin();
        JsonNode page = ok(call(HttpMethod.GET, "/api/admin/dead-letters?status=NEW", admin, null));
        assertThat(page.path("total").asLong()).isPositive();

        ok(call(HttpMethod.POST, "/api/admin/dead-letters/" + dlId + "/replay", admin, null));
        assertThat(jdbc.queryForObject("SELECT status FROM dead_letter_message WHERE id = ?", String.class, dlId))
                .isEqualTo("REPLAYED");
        // 重放的消息依旧是坏的，会再次走完重试进入死信：说明重放确实把消息送回了原队列
        await().atMost(Duration.ofSeconds(20)).until(() -> countDeadLetter(msgId, MqNames.NOTIFY_QUEUE) == 2);
    }

    @Test
    void outboxRelayDeliversUnsentRows() throws Exception {
        TestUser u = newUser();
        String msgId = UUID.randomUUID().toString();
        EventMessage event = new EventMessage(msgId, EventTypes.SYSTEM_NOTICE, LocalDateTime.now(),
                objectMapper.valueToTree(new Payloads.SystemNoticeEvent(u.id(), "补偿投递测试")));
        // 模拟"事务提交了但提交后的即时投递丢了"：只有一条 NEW 状态的本地消息
        jdbc.update("INSERT INTO outbox_message (id, msg_id, exchange, routing_key, payload, status, retry_count, next_retry_time)"
                        + " VALUES (?, ?, ?, ?, ?, 'NEW', 0, NOW(3) - INTERVAL 1 MINUTE)",
                System.nanoTime(), msgId, mqNames.eventExchange(), EventTypes.SYSTEM_NOTICE,
                objectMapper.writeValueAsString(event));

        outboxPublisher.relayDue();

        await().atMost(Duration.ofSeconds(10)).until(() -> "SENT".equals(
                jdbc.queryForObject("SELECT status FROM outbox_message WHERE msg_id = ?", String.class, msgId)));
        await().atMost(Duration.ofSeconds(10)).until(() -> countNotify(msgId) == 1);
    }

    private Message message(String msgId, String type, Object payload) throws Exception {
        EventMessage event = new EventMessage(msgId, type, LocalDateTime.now(), objectMapper.valueToTree(payload));
        return MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(msgId)
                .build();
    }

    private int countNotify(String msgId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notify_message WHERE msg_id = ?", Integer.class, msgId);
    }

    private int countDeadLetter(String msgId, String queue) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dead_letter_message WHERE msg_id = ? AND queue = ?",
                Integer.class, msgId, queue);
    }
}
