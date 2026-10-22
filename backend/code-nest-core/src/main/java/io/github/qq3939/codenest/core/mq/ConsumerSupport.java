package io.github.qq3939.codenest.core.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.qq3939.codenest.api.event.EventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 所有业务消费者的公共流程：解析 -> 处理 -> ack；失败时投到下一级重试队列或死信队列，然后 ack 原消息。
 * 手动 ack 的意义：处理成功之前消息不会从队列删除，消费者进程崩溃时 Broker 会把它重新投给别的消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerSupport {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MqNames names;

    public void handle(String queue, Message message, Channel channel, Consumer<EventMessage> handler) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        EventMessage event;
        try {
            event = objectMapper.readValue(message.getBody(), EventMessage.class);
        } catch (Exception e) {
            // 消息体本身坏了，重试也没用，直接拒绝，由队列的死信交换机送进 dlq
            log.error("unparseable message on {}, reject to dlq", queue, e);
            channel.basicReject(tag, false);
            return;
        }
        try {
            handler.accept(event);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            int retried = retryCount(message.getMessageProperties());
            log.warn("consume failed, queue={}, msgId={}, retried={}", queue, event.msgId(), retried, e);
            try {
                forwardForRetry(queue, message, retried, e);
                channel.basicAck(tag, false);
            } catch (Exception forwardError) {
                // 连转发都失败（例如 Broker 抖动），让消息回到原队列，等下次投递
                log.error("forward failed, nack with requeue, msgId={}", event.msgId(), forwardError);
                channel.basicNack(tag, false, true);
            }
        }
    }

    private void forwardForRetry(String queue, Message original, int retried, Exception error) {
        MessageProperties props = original.getMessageProperties();
        props.setHeader(MqNames.HEADER_RETRY, retried + 1);
        props.setHeader(MqNames.HEADER_ORIGIN_QUEUE, queue);
        if (retried < names.retryDelaysMs().length) {
            rabbitTemplate.send(names.retryExchange(), MqNames.retryQueue(queue, retried + 1), original);
        } else {
            props.setHeader(MqNames.HEADER_ERROR, abbreviate(error));
            rabbitTemplate.send(names.dlxExchange(), queue, original);
        }
    }

    private static int retryCount(MessageProperties props) {
        Object value = props.getHeader(MqNames.HEADER_RETRY);
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static String abbreviate(Exception e) {
        String text = e.getClass().getSimpleName() + ": " + e.getMessage();
        return text.length() > 900 ? text.substring(0, 900) : text;
    }
}
