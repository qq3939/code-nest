package io.github.changeme.projecta.service.outbox;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.changeme.projecta.api.enums.OutboxStatus;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.service.outbox.entity.OutboxMessageDO;
import io.github.changeme.projecta.service.outbox.mapper.OutboxMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 把 outbox 记录投递到 RabbitMQ。开启 publisher confirm（correlated）：
 * Broker 把消息持久化并路由成功后回 ack，这时才把记录标为 SENT；nack、超时、不可路由都留给中继重试。
 */
@Slf4j
@Component
public class OutboxPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final OutboxMessageMapper outboxMapper;
    private final ThreadPoolTaskExecutor executor;
    private final AppProperties props;

    public OutboxPublisher(RabbitTemplate rabbitTemplate, OutboxMessageMapper outboxMapper,
                           @Qualifier("outboxExecutor") ThreadPoolTaskExecutor executor, AppProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxMapper = outboxMapper;
        this.executor = executor;
        this.props = props;
    }

    public void publishAsync(OutboxMessageDO row) {
        executor.execute(() -> send(row));
    }

    /** 中继任务：捞出到期未发送的记录，先推迟下次重试时间再发送，按指数退避。 */
    public int relayDue() {
        List<OutboxMessageDO> due = outboxMapper.selectList(Wrappers.<OutboxMessageDO>lambdaQuery()
                .eq(OutboxMessageDO::getStatus, OutboxStatus.NEW)
                .le(OutboxMessageDO::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(OutboxMessageDO::getNextRetryTime)
                .last("LIMIT " + props.getOutbox().getRelayBatchSize()));
        for (OutboxMessageDO row : due) {
            int retry = row.getRetryCount() + 1;
            if (retry > props.getOutbox().getMaxRetry()) {
                markFailed(row, "exceed max retry");
                continue;
            }
            outboxMapper.update(Wrappers.<OutboxMessageDO>lambdaUpdate()
                    .set(OutboxMessageDO::getRetryCount, retry)
                    .set(OutboxMessageDO::getNextRetryTime, LocalDateTime.now().plus(backoff(retry)))
                    .eq(OutboxMessageDO::getId, row.getId()));
            send(row);
        }
        return due.size();
    }

    Duration backoff(int retry) {
        Duration base = props.getOutbox().getBaseBackoff();
        Duration delay = base.multipliedBy(1L << Math.min(retry, 16));
        return delay.compareTo(props.getOutbox().getMaxBackoff()) > 0 ? props.getOutbox().getMaxBackoff() : delay;
    }

    private void send(OutboxMessageDO row) {
        CorrelationData correlation = new CorrelationData(row.getMsgId());
        correlation.getFuture().whenComplete((confirm, ex) -> {
            if (ex != null || confirm == null || !confirm.isAck()) {
                log.warn("outbox nack/timeout, msgId={}, cause={}", row.getMsgId(),
                        ex != null ? ex.getMessage() : confirm == null ? "null" : confirm.getReason());
                return;
            }
            if (correlation.getReturned() != null) {
                // 不可路由说明没有队列绑定这个路由键，属于配置错误，重试没有意义
                markFailed(row, "unroutable: " + correlation.getReturned().getReplyText());
                return;
            }
            markSent(row);
        });
        Message message = MessageBuilder.withBody(row.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(row.getMsgId())
                .build();
        try {
            rabbitTemplate.send(row.getExchange(), row.getRoutingKey(), message, correlation);
        } catch (Exception e) {
            log.warn("outbox send failed, msgId={}, will relay later: {}", row.getMsgId(), e.getMessage());
        }
    }

    private void markSent(OutboxMessageDO row) {
        outboxMapper.update(Wrappers.<OutboxMessageDO>lambdaUpdate()
                .set(OutboxMessageDO::getStatus, OutboxStatus.SENT)
                .eq(OutboxMessageDO::getId, row.getId())
                .eq(OutboxMessageDO::getStatus, OutboxStatus.NEW));
    }

    private void markFailed(OutboxMessageDO row, String reason) {
        outboxMapper.update(Wrappers.<OutboxMessageDO>lambdaUpdate()
                .set(OutboxMessageDO::getStatus, OutboxStatus.FAILED)
                .eq(OutboxMessageDO::getId, row.getId()));
        // 告警：这里只打 ERROR 日志；接入监控后可以对这条日志或 FAILED 记录数配置告警规则
        log.error("[ALERT] outbox message failed permanently, msgId={}, routingKey={}, reason={}",
                row.getMsgId(), row.getRoutingKey(), reason);
    }
}
