-- 只在 Hash 不存在时用库里的值初始化，并发初始化时后到的不会覆盖先到的（以及先到者之后的增量）。
-- KEYS[1] count:article:{id}；ARGV 依次为 read、like、collect、comment
if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('HSET', KEYS[1], 'read', ARGV[1], 'like', ARGV[2], 'collect', ARGV[3], 'comment', ARGV[4])
end
return 1
