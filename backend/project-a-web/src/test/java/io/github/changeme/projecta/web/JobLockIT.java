package io.github.changeme.projecta.web;

import io.github.changeme.projecta.core.lock.JobLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 定时任务锁（SPEC 14.4"定时任务只在一个实例上执行"，P-018）。两次调用模拟两个实例先后触发同一个任务。 */
class JobLockIT extends IntegrationTestBase {

    @Autowired
    JobLock jobLock;

    @Test
    void lockAloneDoesNotStopASecondRunRightAfterTheFirst() {
        String job = "it-" + UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();
        jobLock.runExclusively(job, runs::incrementAndGet);
        jobLock.runExclusively(job, runs::incrementAndGet);
        // 只有互斥：第一个跑完释放锁，第二个照样能跑——这就是需要最短间隔的原因
        assertThat(runs).hasValue(2);
    }

    @Test
    void minIntervalRunsOncePerPeriodAcrossInstances() {
        String job = "it-" + UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();
        assertThat(jobLock.runExclusively(job, Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();
        assertThat(jobLock.runExclusively(job, Duration.ofMinutes(5), runs::incrementAndGet)).isFalse();
        assertThat(runs).hasValue(1);
    }

    @Test
    void anotherInstanceSkipsWhileTheJobIsRunning() throws Exception {
        String job = "it-" + UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();
        Thread other = new Thread(() -> jobLock.runExclusively(job, () -> {
            runs.incrementAndGet();
            sleep(1500);
        }));
        other.start();
        sleep(300);
        // 锁按线程持有，另一个线程等价于另一个实例
        assertThat(jobLock.runExclusively(job, runs::incrementAndGet)).isFalse();
        other.join();
        assertThat(runs).hasValue(1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
