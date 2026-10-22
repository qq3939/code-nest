package io.github.changeme.projecta.service.outbox;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.api.common.PageResult;
import io.github.changeme.projecta.api.enums.DeadLetterStatus;
import io.github.changeme.projecta.api.vo.DeadLetterVO;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.core.mq.MqNames;
import io.github.changeme.projecta.service.outbox.entity.DeadLetterMessageDO;
import io.github.changeme.projecta.service.outbox.mapper.DeadLetterMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 死信：消费者重试 3 次仍失败的消息进入 Q.dlq，这里把它们落库（然后 ack），
 * 管理员在后台查看失败原因，修复问题后一键重新投递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {
    private final DeadLetterMessageMapper deadLetterMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MqNames names;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = {"notify.queue.dlq", "feed.queue.dlq", "search.queue.dlq", "rank.queue.dlq",
            "cache.retry.queue.dlq"})
    public void onDeadLetter(Message message, Channel channel,
                             @Header(AmqpHeaders.CONSUMER_QUEUE) String dlqName) throws IOException {
        MessageProperties props = message.getMessageProperties();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        DeadLetterMessageDO row = new DeadLetterMessageDO();
        row.setMsgId(props.getMessageId() != null ? props.getMessageId() : extractMsgId(payload));
        row.setQueue(dlqName.substring(0, dlqName.length() - ".dlq".length()));
        row.setPayload(isJson(payload) ? payload : objectMapper.valueToTree(payload).toString());
        Object error = props.getHeader(MqNames.HEADER_ERROR);
        row.setError(error != null ? error.toString() : "rejected (unparseable or requeue disabled)");
        row.setFailedTime(LocalDateTime.now());
        row.setStatus(DeadLetterStatus.NEW);
        deadLetterMapper.insert(row);
        log.warn("dead letter stored, queue={}, msgId={}", row.getQueue(), row.getMsgId());
        channel.basicAck(props.getDeliveryTag(), false);
    }

    public PageResult<DeadLetterVO> page(DeadLetterStatus status, long page, long size) {
        Page<DeadLetterMessageDO> result = deadLetterMapper.selectPage(Page.of(page, size),
                Wrappers.<DeadLetterMessageDO>lambdaQuery()
                        .eq(status != null, DeadLetterMessageDO::getStatus, status)
                        .orderByDesc(DeadLetterMessageDO::getFailedTime));
        return new PageResult<>(result.getRecords().stream().map(DeadLetterService::toVO).toList(),
                result.getTotal(), page, size);
    }

    /** 重新投递：经 retry 交换机、以原队列名为路由键，只回到原来那一个队列，不会让其他订阅者重复收到。 */
    public void replay(Long id) {
        DeadLetterMessageDO row = deadLetterMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DEAD_LETTER_NOT_FOUND);
        }
        Message message = MessageBuilder.withBody(row.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(row.getMsgId())
                .build();
        rabbitTemplate.send(names.retryExchange(), row.getQueue(), message);
        row.setStatus(DeadLetterStatus.REPLAYED);
        deadLetterMapper.updateById(row);
    }

    public void ignore(Long id) {
        DeadLetterMessageDO row = deadLetterMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DEAD_LETTER_NOT_FOUND);
        }
        row.setStatus(DeadLetterStatus.IGNORED);
        deadLetterMapper.updateById(row);
    }

    private String extractMsgId(String payload) {
        try {
            return objectMapper.readTree(payload).path("msgId").asText("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isJson(String text) {
        try {
            objectMapper.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static DeadLetterVO toVO(DeadLetterMessageDO d) {
        return new DeadLetterVO(d.getId(), d.getMsgId(), d.getQueue(), d.getPayload(), d.getError(), d.getFailedTime(),
                d.getStatus().name());
    }
}
