package io.github.changeme.projecta.core.lock;

import io.github.changeme.projecta.core.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时任务的分布式锁：多实例部署时同一任务只在一个实例上执行。
 * tryLock 不等待——拿不到说明别的实例正在跑，这一轮直接跳过即可；
 * 不传 leaseTime，让看门狗自动续期，任务执行再久也不会因锁过期被别的实例重复执行。
 * <p>
 * 只有锁还不够：锁只保证"不同时跑"。两个实例的定时器触发时间差几百毫秒，而任务几十毫秒就跑完，
 * 后到的实例照样拿得到锁、再跑一遍（P-018）。所以低频任务再加一个"最短间隔"标记：
 * 拿到锁之后 SET NX 一个带过期时间的 key，设置失败说明这个周期别的实例已经跑过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobLock {
    private final RedissonClient redisson;
    private final RedisKeys keys;

    /** 高频且幂等的任务（计数写回、outbox 中继）：多跑一次无害，只需要互斥。 */
    public boolean runExclusively(String jobName, Runnable task) {
        return runExclusively(jobName, Duration.ZERO, task);
    }

    /** minInterval 内同一个任务在所有实例中最多执行一次，取值应小于任务周期。 */
    public boolean runExclusively(String jobName, Duration minInterval, Runnable task) {
        RLock lock = redisson.getLock(keys.jobLock(jobName));
        if (!lock.tryLock()) {
            log.debug("job {} is running on another instance, skip", jobName);
            return false;
        }
        try {
            if (!minInterval.isZero() && !redisson.getBucket(keys.jobLock(jobName) + ":ran", StringCodec.INSTANCE)
                    .setIfAbsent("1", minInterval)) {
                log.debug("job {} already ran on another instance in this period, skip", jobName);
                return false;
            }
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
