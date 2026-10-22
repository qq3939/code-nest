package io.github.qq3939.codenest.core.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 声明整套拓扑。每个业务队列 Q 配套：
 * <pre>
 *   xxx.event (topic) --路由键--> Q
 *   消费失败 -> xxx.retry --Q.retry.n--> Q.retry.n (TTL 1s/5s/30s，死信交换机指回 xxx.retry，路由键 Q) -> Q
 *   第 3 次重试仍失败 -> xxx.dlx --Q--> Q.dlq -> 死信消费者落库
 * </pre>
 * 重试用"多级 TTL 队列"而不是消费者里 sleep：sleep 会占住消费线程和预取的消息，拖慢整个队列。
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public Declarables rabbitTopology(MqNames names) {
        List<Declarable> list = new ArrayList<>();
        TopicExchange event = new TopicExchange(names.eventExchange(), true, false);
        DirectExchange retry = new DirectExchange(names.retryExchange(), true, false);
        DirectExchange dlx = new DirectExchange(names.dlxExchange(), true, false);
        list.add(event);
        list.add(retry);
        list.add(dlx);

        MqNames.BINDINGS.forEach((queueName, routingKeys) -> {
            // 业务队列自身也配死信交换机：消费者若直接 reject（例如消息体无法解析），消息不会丢，而是进 dlq
            Queue queue = QueueBuilder.durable(queueName)
                    .deadLetterExchange(names.dlxExchange())
                    .deadLetterRoutingKey(queueName)
                    .build();
            list.add(queue);
            routingKeys.forEach(key -> list.add(BindingBuilder.bind(queue).to(event).with(key)));
            // 重试队列过期后经 retry 交换机、以队列名为路由键回到原队列
            list.add(BindingBuilder.bind(queue).to(retry).with(queueName));

            long[] delays = names.retryDelaysMs();
            for (int i = 0; i < delays.length; i++) {
                String retryName = MqNames.retryQueue(queueName, i + 1);
                Queue retryQueue = QueueBuilder.durable(retryName)
                        .ttl((int) delays[i])
                        .deadLetterExchange(names.retryExchange())
                        .deadLetterRoutingKey(queueName)
                        .build();
                list.add(retryQueue);
                list.add(BindingBuilder.bind(retryQueue).to(retry).with(retryName));
            }

            Queue dlq = QueueBuilder.durable(MqNames.deadLetterQueue(queueName)).build();
            list.add(dlq);
            Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(queueName);
            list.add(dlqBinding);
        });
        return new Declarables(list);
    }
}
