-- 往一个粉丝的收件箱里推一篇文章，并把收件箱修剪到上限。
-- 只推给"收件箱已存在"的粉丝：不存在说明他很久没来（已过期）或从没看过，等他来读时再从库里冷启动重建，
-- 否则会造出一个只有最新几条、缺了历史的残缺收件箱。
-- KEYS[1] feed:inbox:{userId}；ARGV[1] 文章 ID，ARGV[2] 发布时间（毫秒），ARGV[3] 上限
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end
redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -(tonumber(ARGV[3]) + 1))
return 1
