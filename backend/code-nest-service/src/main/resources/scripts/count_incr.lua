-- redis-batch 计数：HINCRBY + 记脏，一次原子完成。
-- KEYS[1] count:article:{id}，KEYS[2] count:dirty:article；ARGV[1] 字段，ARGV[2] 增量，ARGV[3] 文章 ID
-- Hash 不存在时返回 -1，由 Java 从库里加载初值后重试——
-- 不能直接 HINCRBY：空 Hash 会从 0 开始加，写回绝对值时就把库里原有的计数覆盖成了很小的数。
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end
local v = redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
if v < 0 then
    redis.call('HSET', KEYS[1], ARGV[1], 0)
    v = 0
end
redis.call('SADD', KEYS[2], ARGV[3])
return v
