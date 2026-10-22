package io.github.changeme.projecta.web;

import io.github.changeme.projecta.service.count.AtomicSqlCountService;
import io.github.changeme.projecta.service.count.CountField;
import io.github.changeme.projecta.service.count.CountService;
import io.github.changeme.projecta.service.count.NaiveCountService;
import io.github.changeme.projecta.service.count.RedisBatchCountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 计数三种实现的并发正确性（SPEC 9.2 验收）：200 个线程各加 5 次。
 * naive 先读后写会丢更新，所以只断言"不多于 1000"并打印丢失数；另外两种必须正好 1000。
 */
class CountConcurrencyIT extends IntegrationTestBase {
    private static final int THREADS = 200;
    private static final int TIMES = 5;

    @Autowired
    NaiveCountService naive;
    @Autowired
    AtomicSqlCountService atomic;
    @Autowired
    RedisBatchCountService redisBatch;

    @Test
    void naiveLosesUpdates() throws Exception {
        long id = randomArticleId();
        hammer(naive, id);
        long read = readCountInDb(id);
        System.out.printf("[count] naive: expected %d, got %d, lost %d%n", THREADS * TIMES, read, THREADS * TIMES - read);
        assertThat(read).isLessThanOrEqualTo(THREADS * TIMES);
    }

    @Test
    void atomicSqlIsExact() throws Exception {
        long id = randomArticleId();
        hammer(atomic, id);
        assertThat(readCountInDb(id)).isEqualTo(THREADS * TIMES);
    }

    @Test
    void redisBatchIsExactAfterFlush() throws Exception {
        long id = randomArticleId();
        hammer(redisBatch, id);
        assertThat(redisBatch.get(id).read()).isEqualTo(THREADS * TIMES);
        redisBatch.flush();
        assertThat(readCountInDb(id)).isEqualTo(THREADS * TIMES);
        // 写回的是绝对值，重复写回不会翻倍
        redisBatch.flush();
        assertThat(readCountInDb(id)).isEqualTo(THREADS * TIMES);
    }

    private void hammer(CountService service, long articleId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < TIMES; i++) {
                        service.increase(articleId, CountField.READ, 1);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
    }

    private long readCountInDb(long articleId) {
        Long v = jdbc.queryForObject("SELECT read_count FROM article_stat WHERE article_id = ?", Long.class, articleId);
        return v == null ? 0 : v;
    }

    /** 计数表不依赖文章表的外键，直接用一个不会冲突的大 ID。 */
    private static long randomArticleId() {
        return 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
    }
}
