package io.github.changeme.projecta.web.job;

import io.github.changeme.projecta.api.enums.CountStrategy;
import io.github.changeme.projecta.core.config.RuntimeStrategy;
import io.github.changeme.projecta.core.lock.JobLock;
import io.github.changeme.projecta.service.article.mapper.ArticleMapper;
import io.github.changeme.projecta.service.count.RedisBatchCountService;
import io.github.changeme.projecta.service.outbox.OutboxPublisher;
import io.github.changeme.projecta.service.rank.RankService;
import io.github.changeme.projecta.service.stat.StatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 全部定时任务（SPEC 9.10）。每个任务都套一层 Redisson 分布式锁：多实例部署时同一时刻只有一个实例在跑，
 * 拿不到锁的实例直接跳过这一轮。app.jobs.enabled=false 可整体关闭（集成测试里手动触发更可控）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {
    private static final int RECONCILE_BATCH = 500;

    private final JobLock jobLock;
    private final RuntimeStrategy runtime;
    private final RedisBatchCountService redisBatchCountService;
    private final OutboxPublisher outboxPublisher;
    private final RankService rankService;
    private final StatService statService;
    private final ArticleMapper articleMapper;

    @Scheduled(fixedDelayString = "${app.count.flush-interval:10s}")
    public void flushCounts() {
        if (runtime.countStrategy() == CountStrategy.REDIS_BATCH) {
            jobLock.runExclusively("count-flush", redisBatchCountService::flush);
        }
    }

    @Scheduled(fixedDelay = 5_000)
    public void relayOutbox() {
        jobLock.runExclusively("outbox-relay", outboxPublisher::relayDue);
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 600_000)
    public void refreshHotArticles() {
        jobLock.runExclusively("hot-score", Duration.ofMinutes(5), () -> log.info("hot articles refreshed: {}", rankService.refreshHotArticles()));
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    public void archiveRank() {
        jobLock.runExclusively("rank-archive", Duration.ofHours(1), () -> rankService.archive(LocalDate.now().minusDays(1)));
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Shanghai")
    public void archiveDailyStat() {
        jobLock.runExclusively("daily-stat", Duration.ofHours(1), () -> statService.archiveDay(LocalDate.now().minusDays(1)));
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    public void reconcileCounts() {
        jobLock.runExclusively("count-reconcile", Duration.ofHours(1), this::reconcileAll);
    }

    /** 按 ID 游标遍历全部文章，每批 500 篇对账。 */
    public void reconcileAll() {
        long afterId = 0;
        int fixed = 0;
        while (true) {
            List<Long> ids = articleMapper.selectIdsAfter(afterId, RECONCILE_BATCH);
            if (ids.isEmpty()) {
                break;
            }
            fixed += redisBatchCountService.reconcile(ids);
            afterId = ids.get(ids.size() - 1);
        }
        log.info("count reconcile finished, {} articles fixed", fixed);
    }
}
