-- 排行积分：去重 + 加分 + 设过期，一次原子完成。
-- 如果先 SETNX 去重、再单独 ZINCRBY，两步之间进程崩溃会导致"去重标记写了，分却没加上"，重试时又被当成重复消息跳过。
-- KEYS[1] rank:dedup:{msgId}，KEYS[2] rank:active:day:{date}；ARGV[1] 作者 ID，ARGV[2] 分数，ARGV[3] 日榜过期秒数，ARGV[4] 去重标记过期秒数
if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[4]) == false then
    return 0
end
redis.call('ZINCRBY', KEYS[2], ARGV[2], ARGV[1])
redis.call('EXPIRE', KEYS[2], ARGV[3])
return 1
