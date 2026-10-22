package io.github.qq3939.codenest.service.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 业务里用到的两个线程池。显式定义而不是用默认的 SimpleAsyncTaskExecutor（每次新建线程、无上限）。
 * TaskDecorator 把提交线程的 MDC（traceId）带进异步线程，日志才能串起来。
 */
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor outboxExecutor() {
        return build("outbox-", 2, 8, 2000);
    }

    @Bean
    public ThreadPoolTaskExecutor cacheRebuildExecutor() {
        return build("cache-rebuild-", 1, 4, 100);
    }

    private static ThreadPoolTaskExecutor build(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        // 队列满了由调用线程自己执行：outbox 投递宁可慢一点也不能丢（丢了也有中继兜底，但没必要）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcPropagation());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    private static TaskDecorator mdcPropagation() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
