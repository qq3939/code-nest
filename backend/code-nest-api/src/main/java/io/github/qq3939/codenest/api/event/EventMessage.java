package io.github.qq3939.codenest.api.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 所有领域事件统一的消息体 {msgId, eventType, occurredAt, payload}。
 * payload 用 JsonNode 而不是泛型：一个队列会收到多种事件，消费者先看 eventType 再决定反序列化成哪个类型。
 */
public record EventMessage(String msgId, String eventType, LocalDateTime occurredAt, JsonNode payload) {
}
