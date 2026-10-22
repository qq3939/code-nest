package io.github.qq3939.codenest.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.qq3939.codenest.api.enums.OutboxStatus;
import io.github.qq3939.codenest.api.event.EventMessage;
import io.github.qq3939.codenest.core.mq.MqNames;
import io.github.qq3939.codenest.service.outbox.entity.OutboxMessageDO;
import io.github.qq3939.codenest.service.outbox.mapper.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 本地消息表的写入端（SPEC 9.5）。
 * 业务数据和 outbox 记录在同一个事务里提交：要么都成功，要么都回滚，从根上避免"写库成功但消息没发出去"。
 * 事务提交后立即异步投递一次；投递失败或确认超时的，由 OutboxRelayJob 定时补发。
 */
@Service
@RequiredArgsConstructor
public class OutboxService {
    /** 首次投递后多久还没确认就交给中继补发。 */
    private static final long FIRST_RELAY_DELAY_SECONDS = 10;

    private final OutboxMessageMapper outboxMapper;
    private final OutboxPublisher publisher;
    private final ObjectMapper objectMapper;
    private final MqNames names;

    /** MANDATORY：没有外层事务就直接报错，防止有人在事务外调用、失去原子性。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public String save(String eventType, Object payload) {
        String msgId = UUID.randomUUID().toString().replace("-", "");
        EventMessage event = new EventMessage(msgId, eventType, LocalDateTime.now(), objectMapper.valueToTree(payload));
        OutboxMessageDO row = new OutboxMessageDO();
        row.setMsgId(msgId);
        row.setExchange(names.eventExchange());
        row.setRoutingKey(eventType);
        row.setPayload(toJson(event));
        row.setStatus(OutboxStatus.NEW);
        row.setRetryCount(0);
        row.setNextRetryTime(LocalDateTime.now().plusSeconds(FIRST_RELAY_DELAY_SECONDS));
        outboxMapper.insert(row);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishAsync(row);
            }
        });
        return msgId;
    }

    private String toJson(EventMessage event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
