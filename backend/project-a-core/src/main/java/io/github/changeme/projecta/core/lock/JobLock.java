package io.github.changeme.projecta.core.lock;

import io.github.changeme.projecta.core.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 定时任务的分布式锁：多实例部署时同一任务只在一个实例上执行。
 * tryLock 不等待——拿不到说明别的实例正在跑，这一轮直接跳过即可；
 * 不传 leaseTime，让看门狗自动续期，任务执行再久也不会因锁过期被别的实例重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobLock {
    private final RedissonClient redisson;
    private final RedisKeys keys;

    public boolean runExclusively(String jobName, Runnable task) {
        RLock lock = redisson.getLock(keys.jobLock(jobName));
        if (!lock.tryLock()) {
            log.debug("job {} is running on another instance, skip", jobName);
            return false;
        }
        try {
            task.run();
            return true;
        } catch (Exception e) {
            log.error("job {} failed", jobName, e);
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
