-- ZSet 滑动窗口限流，多个维度一次原子判断。
-- KEYS: 各维度的限流 key；ARGV[1] 当前毫秒时间，ARGV[2] 窗口毫秒数，ARGV[3] 上限，ARGV[4] 本次请求的唯一成员
-- 返回 1 表示放行，0 表示超限。
-- 先全部检查再全部写入：如果边查边写，USER 维度放行、IP 维度拒绝时，USER 维度会白白记一次。
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
for i = 1, #KEYS do
    redis.call('ZREMRANGEBYSCORE', KEYS[i], 0, now - window)
    if redis.call('ZCARD', KEYS[i]) >= limit then
        return 0
    end
end
for i = 1, #KEYS do
    redis.call('ZADD', KEYS[i], now, ARGV[4])
    redis.call('PEXPIRE', KEYS[i], window)
end
return 1
