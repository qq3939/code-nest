# 设计决策记录

每个关键选型记一条，编号依次递增。重点写清"为什么"，面试时会被追问。
路径里的 `…/` 是 `backend/<模块>/src/main/java/io/github/qq3939/codenest/` 的缩写。

## D-000 模板（复制后填写）

- **背景**：要解决什么问题，有哪些约束。
- **方案**：最终采用的做法。
- **备选**：考虑过的其他做法，以及各自的优缺点。
- **为什么选它**：
- **代价与风险**：
- **相关代码**：`路径:行号`

---

## D-001 版本锁定：Spring Boot 3.5.16 + Java 17 字节码

- **背景**：SPEC 要求 Java 17 + Spring Boot 3.5，版本"开工时取最新稳定小版本后锁定"。
- **方案**：父 POM 继承 `spring-boot-starter-parent:3.5.16`，`maven.compiler.release=17`；其余依赖在父 POM 的 `<properties>` 里写死：MyBatis-Plus 3.5.17、Redisson 3.52.0、jjwt 0.12.7、springdoc 2.8.17、MinIO SDK 8.5.17。前端 `package.json` 全部写精确版本（不带 `^`），并提交 `package-lock.json`。
- **备选**：用版本范围或 `latest`——每次构建可能拿到不同版本，"昨天能跑今天不能跑"。
- **为什么选它**：可复现。用 `release=17` 而不是 `source/target`，编译器会按 JDK 17 的 API 检查，即使本机装的是更高版本 JDK 也不会误用新 API。
- **代价与风险**：要手动升级，安全补丁不会自动跟进。
- **相关代码**：`backend/pom.xml:28`、`frontend/package.json`

## D-002 项目名和包名先用占位符

- **背景**：SPEC 第 1 节要求开工前确认项目正式名称和 GitHub 用户名，用户要求"直接做出最完整的版本"，没有提供这两项。
- **方案**：项目名先用 `project-a`，包名 `io.github.changeme.projecta`，提供 `scripts/rename-project.sh` 一键替换（目录、包名、groupId、Redis 前缀、Compose 名称）。
- **备选**：停下来等用户回复——用户明确要求直接做。
- **为什么选它**：不阻塞进度，改名是机械操作，脚本化以后几秒钟完成。
- **代价与风险**：改名后需要重新 `docker compose down -v`（Redis key 前缀、MinIO 桶名都会变）。
- **相关代码**：`scripts/rename-project.sh`

## D-003 关联表和计数表用业务键做主键

- **背景**：SPEC 第 6 节规定 article_tag 的主键是 (article_id, tag_id)，article_stat、user_stat 一行对应一个文章/用户。
- **方案**：这三张表不设 `id` 列，直接用业务键做主键；其余表用雪花 ID（见 D-004）。
- **备选**：每张表都加自增/雪花 `id`，再给业务键加唯一索引。
- **为什么选它**：InnoDB 的聚簇索引就是主键。用业务键做主键，按文章查计数是一次主键查找，不需要回表；`INSERT … ON DUPLICATE KEY UPDATE` 也天然按主键去重，计数写回（D-008）可以直接 upsert。
- **代价与风险**：MyBatis-Plus 的 `BaseMapper.updateById` 需要用 `@TableId` 标注业务键字段，联合主键的 article_tag 不能用 `selectById`，只能用条件构造器。
- **相关代码**：`backend/code-nest-web/src/main/resources/db/migration/V1__init_schema.sql:61`、`:119`、`:129`

## D-004 主键用雪花 ID，由应用生成

- **背景**：多实例部署，Feed 和游标分页需要按时间排序，分库分表是将来可能的演进方向。
- **方案**：`BaseDO` 上 `@TableId(type = IdType.ASSIGN_ID)`，MyBatis-Plus 用雪花算法生成 64 位 ID。
- **备选**：MySQL 自增（简单，但 ID 连续可被遍历，将来拆库时冲突）；UUID（无序，导致 B+ 树页分裂，且 36 字节太大）。
- **为什么选它**：趋势递增，对 InnoDB 友好；不依赖数据库；8 字节。
- **代价与风险**：超过 2^53，JavaScript 的 Number 存不下，必须序列化成字符串（见 P-001）；依赖机器时钟，时钟回拨可能产生重复 ID（MyBatis-Plus 的实现在小幅回拨时会等待）。
- **相关代码**：`…/service/common/BaseDO.java:14`、`…/core/config/JacksonConfig.java:20`

## D-005 不用 Spring Security，自己写拦截器 + JWT

- **背景**：需要登录鉴权、管理员权限、令牌刷新。
- **方案**：`AuthInterceptor` 解析 `Authorization: Bearer <jwt>`，把 `LoginUser` 放进 `UserContext`（ThreadLocal）；`/api/admin/**` 要求 ADMIN 角色；公开接口用 `@Public` 注解放行。密码哈希只引入 `spring-security-crypto` 的 BCrypt。
- **备选**：Spring Security 全家桶——功能全，但过滤器链、`SecurityContext`、各种配置对学习者是黑盒，调试成本高。
- **为什么选它**：鉴权需求简单（登录用户 / 管理员两级），自己写不到 100 行，每一步都能下断点看明白。
- **代价与风险**：方法级权限、CSRF、CORS 等都要自己处理（本项目令牌放请求头、前后端同源，所以不需要 CSRF）。
- **相关代码**：`…/web/interceptor/AuthInterceptor.java:28`、`…/core/security/JwtUtil.java:24`

## D-006 双令牌：短期 JWT + Redis 里的刷新令牌，刷新时轮换

- **背景**：JWT 无状态、校验快，但签发后无法撤销；封禁、改密码要求立即失效。
- **方案**：access token 是 30 分钟有效的 JWT；refresh token 是随机串，存在 Redis（`auth:refresh:{userId}:{tokenId}`，7 天）。刷新时用 `DEL` 的返回值判断"存在并删除"，再签发一对新令牌（轮换）。封禁和改密码时 SCAN 删除该用户全部刷新令牌，同时清掉用户状态缓存，拦截器每次请求都会检查用户状态（有 10 分钟缓存）。
- **备选**：只用一个长期 JWT（无法撤销）；access token 也存 Redis（每个请求查一次 Redis，失去 JWT 的意义）。
- **为什么选它**：绝大多数请求只做 JWT 验签；撤销能力集中在刷新环节。`DEL` 是原子的，同一个刷新令牌被并发使用时只有一个请求能成功，旧令牌泄露后被用过一次就作废。
- **代价与风险**：封禁后，已签发的 access token 依靠"用户状态缓存被清除 + 每次请求校验状态"才能立即失效，多了一次缓存读取。
- **相关代码**：`…/service/user/AuthService.java:84`、`…/service/user/AuthService.java:111`

## D-007 Redis 持久化用 AOF everysec，计数靠对账兜底

- **背景**：计数（redis-batch）、Feed、排行榜都放在 Redis，Redis 宕机会丢数据。
- **方案**：Compose 里 Redis 开 `--appendonly yes --appendfsync everysec`，最多丢 1 秒。计数每 10 秒写回 MySQL，每天 03:00 跑对账任务：点赞、收藏、评论数以明细表为准重算；阅读数没有明细，取 Redis 和库里较大的值。Feed 收件箱丢了会在下次读取时从 MySQL 冷启动重建；榜单可以从明细重算。
- **备选**：`appendfsync always`（每次写都落盘，性能下降明显）；只用 RDB（可能丢几分钟）。
- **为什么选它**：Redis 里的数据都能从 MySQL 推导出来，Redis 定位是"可丢失的加速层"，丢 1 秒可以接受。
- **代价与风险**：阅读数在 Redis 宕机时最多丢最后一个写回周期（10 秒）的增量。
- **相关代码**：`docker-compose.yml:34`、`…/service/count/RedisBatchCountService.java:145`、`…/web/job/ScheduledJobs.java:66`

## D-008 计数写回写"绝对值"而不是"增量"

- **背景**：redis-batch 策略里，Redis 是计数的实时值，需要定期写回 MySQL。
- **方案**：写回时 `SPOP` 一批脏文章 ID，读出它们在 Redis Hash 里的当前值，用 `INSERT … ON DUPLICATE KEY UPDATE` 直接覆盖库里的值。写库失败就把 ID 放回脏集合。
- **备选**：在 Redis 里累积增量，写回时 `UPDATE x = x + delta` 并清零增量。
- **为什么选它**：写绝对值是幂等的：写回重复执行、两个实例同时写回、失败重试，结果都一样。写增量的话，"写库成功但清零失败"会导致重复累加。
- **代价与风险**：Redis 的值一旦错了（比如被误删后从 0 开始累加），会把错误值覆盖进库，所以初始化 Hash 时一定从库里加载（`count_init.lua`），并靠每日对账修正。
- **相关代码**：`…/service/count/RedisBatchCountService.java:110`、`backend/code-nest-service/src/main/resources/mapper/ArticleStatMapper.xml:22`

## D-009 user_stat 的"获赞数、阅读数"读时汇总，不实时维护

- **背景**：作者的获赞总数、阅读总数等于他所有文章计数之和。如果每次点赞都去更新作者的 user_stat，热门作者那一行会成为写热点。
- **方案**：文章数、粉丝数、关注数在业务发生时增减；`like_received`、`read_received` 在打开个人主页时用一条聚合 SQL 从 article_stat 汇总并写回 user_stat。
- **备选**：每次点赞同时 `UPDATE user_stat SET like_received = like_received + 1`。
- **为什么选它**：避免行锁热点；个人主页的访问频率远低于点赞。
- **代价与风险**：作者文章特别多时，这条聚合 SQL 要扫描他的全部文章（走 `idx_author_status_publish` 索引）；可以再加一层缓存。
- **相关代码**：`backend/code-nest-service/src/main/resources/mapper/UserStatMapper.xml:20`

## D-010 Feed：收件箱 + 发件箱，hybrid 下大 V 走拉模式

- **背景**：关注流有推（写扩散）和拉（读扩散）两种经典做法。大 V 有几千上万个粉丝，推模式下一篇文章要写几千次 Redis。
- **方案**：每个用户一个收件箱 ZSet（`feed:inbox:{uid}`，最多 500 条），每个作者一个发件箱 ZSet（`feed:outbox:{uid}`，最多 1000 条），score 是发布时间毫秒。hybrid 模式下，普通作者发文扇出到粉丝收件箱；粉丝数 ≥ 阈值（默认 5000）的大 V 只写发件箱，读的时候把"收件箱 + 关注的大 V 的发件箱"做多路归并。只推给已存在的收件箱，不存在的等用户来读时从 MySQL 冷启动重建。读时还要按文章当前状态和当前关注关系过滤，保证删文、取关立即生效。
- **备选**：纯推（大 V 发文写放大，粉丝多时扇出慢、内存大）；纯拉（每次读都要合并所有关注者的发件箱，关注多时读放大）。
- **为什么选它**：读多写少的社区，普通用户占绝大多数，推模式读最快；只有少数大 V 改成拉，归并的路数很少。三种模式都实现了，可以在实验开关里切换并压测对比（见 `docs/perf-report.md`）。
- **代价与风险**：分页游标要能跨多个来源：用"上一页最后一条的 score + 同 score 已返回的条数"做游标（`FeedCursor`），避免同一毫秒发布的文章被重复或漏掉。
- **相关代码**：`…/service/feed/FeedService.java:98`、`…/service/feed/FeedMerger.java:29`、`backend/code-nest-service/src/main/resources/scripts/feed_push.lua`

## D-011 可靠消息：本地消息表（outbox）而不是 RocketMQ 事务消息

- **背景**：发文、点赞等写库操作之后要发 MQ 消息（通知、Feed、搜索、排行）。"写库成功但消息没发出去"或"消息发了但事务回滚"都会造成数据不一致。
- **方案**：业务事务里同时往 `outbox_message` 插一行（`Propagation.MANDATORY` 强制必须在事务里调用），事务提交后（`afterCommit`）立即异步投递；投递用 publisher confirm 确认 Broker 收到后把状态改成 SENT。中继任务每 5 秒扫描超时未确认的 NEW 记录重新投递，按指数退避。
- **备选**：RocketMQ 事务消息（半消息 + 回查，需要换 MQ）；先发消息再写库（无法回滚已发出的消息）；只在 afterCommit 里发（进程崩溃就丢）。
- **为什么选它**：只依赖 MySQL 事务，任何 MQ 都适用；消息和业务数据在同一个事务里，要么都有要么都没有。
- **代价与风险**：消息可能重复（投递成功但确认回来前进程崩溃，中继会再发一次），所以消费者必须幂等（D-013）；outbox 表需要定期清理已发送的记录。
- **相关代码**：`…/service/outbox/OutboxService.java:37`、`…/service/outbox/OutboxPublisher.java:49`

## D-012 消费失败：多级 TTL 重试队列 + 死信表

- **背景**：消费者偶发失败（数据库抖动）需要重试；一直失败的消息不能无限重试，也不能丢。
- **方案**：手动 ack。消费失败时，把消息转发到对应的重试队列 `xxx.retry.1/2/3`（TTL 分别 1 秒、5 秒、30 秒，过期后通过死信交换机回到原队列），消息头记录重试次数；三次都失败就送进 `xxx.dlq`，死信消费者把它写进 `dead_letter_message` 表，管理端可以查看、重放或忽略。
- **备选**：`basicNack(requeue=true)`（立即重回队首，失败的消息会刷屏，阻塞后续消息）；RabbitMQ 延迟消息插件（需要装插件）；Spring Retry 在消费线程里重试（占着消费线程睡眠）。
- **为什么选它**：不需要插件；重试期间不占消费线程；死信落库后可以人工处理。每一级是独立队列，避免"队首消息 TTL 未到、后面短 TTL 的消息被卡住"的问题。
- **代价与风险**：队列数量多（每个业务队列 3 个重试队列 + 1 个死信队列）；重试顺序和原始顺序不同（见 D-014）。
- **相关代码**：`…/core/mq/ConsumerSupport.java:28`、`…/core/mq/RabbitTopologyConfig.java:27`、`…/service/outbox/DeadLetterService.java:46`

## D-013 消费幂等：每个消费者用最适合自己的手段

- **背景**：至少一次投递 + 重试，同一条消息可能被消费多次。
- **方案**：通知消费者靠 `notify_message.msg_id` 唯一索引，重复插入时捕获 `DuplicateKeyException` 直接确认；排行加分用 Lua 脚本把"`SET NX rank:dedup:{msgId}` + `ZINCRBY`"放在一起原子执行；Feed 的 ZADD/ZREM、搜索的按 ID upsert/delete、删缓存本身就是幂等的。
- **备选**：统一用一张"已消费消息表"——每次消费多一次写库，且要和业务操作放进同一个事务。
- **为什么选它**：利用操作本身的特性，零额外成本。
- **代价与风险**：新加消费者时必须想清楚它是否幂等，代码注释里每个 `@RabbitListener` 上都写了幂等手段。
- **相关代码**：`…/service/mq/EventConsumers.java:31`、`…/service/notify/NotifyService.java:45`、`backend/code-nest-service/src/main/resources/scripts/rank_add.lua`

## D-014 不保证消息顺序，消费时以数据库当前状态为准

- **背景**：重试会打乱顺序：例如"发文"消息重试期间，"删文"消息先被消费了。
- **方案**：消费者不信任消息里的状态，而是回查数据库：Feed 扇出前检查文章当前是否还是 PUBLISHED；关注/取关消息到达时查当前是否仍在关注。
- **备选**：单队列单消费者保证顺序（吞吐受限，且重试仍会乱序）；消息带版本号。
- **为什么选它**：简单且正确：消息只是"有变化，来看看"的通知。
- **代价与风险**：每条消息多一次查库。
- **相关代码**：`…/service/feed/FeedService.java:248`

## D-015 两级缓存：本地 Caffeine + Redis，失效靠广播和 MQ 补偿

- **背景**：热点文章详情读多写少；多实例部署时，每台机器的本地缓存需要一起失效。
- **方案**：读：Caffeine（60 秒）→ Redis（30 分钟 + 0–5 分钟随机抖动）→ MySQL。写：先更新数据库，事务提交后删 Redis、删本地，并通过 Redis Pub/Sub 广播让其他实例删本地缓存；删 Redis 失败时发一条 `cache.delete` 消息到 MQ，由消费者重试删除。三大问题：空值缓存防穿透；重建时加互斥锁防击穿；TTL 加随机抖动防雪崩；首页热门列表这种"必须一直有值"的 key 用逻辑过期，过期后一个线程异步重建，其他请求返回旧值。
- **备选**：先删缓存再更新库（并发读会把旧值写回缓存）；延迟双删（等待时间难定）；Canal 订阅 binlog 删缓存（多一个组件）。
- **为什么选它**：Cache-Aside + 提交后删除是业界最常用的做法，配合 MQ 补偿基本保证最终一致；本地缓存 60 秒 TTL 是广播丢失时的兜底。
- **代价与风险**：极端情况下（读请求在更新前读到旧值、在删除后才写回缓存）仍可能短暂不一致，最多持续到 TTL 过期。
- **相关代码**：`…/service/cache/CacheService.java:105`、`…/service/cache/CacheService.java:170`、`…/service/cache/CacheService.java:231`

## D-016 实验开关在 dev 和 docker profile 默认开启，prod 关闭

- **背景**：SPEC 9.14 要求"dev 环境提供运行时切换策略的接口，prod 关闭"。压测是用 `docker compose` 跑的，用的是 docker profile。
- **方案**：`app.dev-tools.enabled` 在 dev 为 true；docker profile 读 `DEV_TOOLS_ENABLED`，默认 true；prod 为 false，接口返回 404。切换通过 Redis `strategy:refresh` 频道广播到所有实例。
- **备选**：docker profile 默认关闭——那压测时还要改环境变量，容易忘。
- **为什么选它**：本项目的 docker profile 就是本机实验环境。
- **代价与风险**：真正部署到公网时必须用 prod profile 或设 `DEV_TOOLS_ENABLED=false`，README 里有提醒。
- **相关代码**：`backend/code-nest-web/src/main/resources/application-docker.yml:18`、`…/service/admin/StrategyService.java:53`

## D-017 看板：当天数据在 Redis，凌晨归档到 daily_stat

- **背景**：看板要显示日活、新增用户、新增文章、阅读、点赞的每日趋势。
- **方案**：日活用 HyperLogLog（`uv:day:{date}`，登录用户记 `u:{userId}`、游客记 `ip:{ip}`，12KB 存任意多用户，误差约 0.81%）；阅读和点赞用 Hash `stat:daily:{date}` 的 HINCRBY；每天 00:10 把前一天的数据写进 `daily_stat` 表。看板查询时，历史数据读表，当天数据读 Redis。
- **备选**：日活用 Set（精确，但 10 万用户要几 MB）；每次请求写 MySQL（写放大）。
- **为什么选它**：看板只看趋势，不需要精确到个位；HyperLogLog 内存固定。
- **代价与风险**：日活有约 1% 的误差；Redis 数据在归档前丢失会导致当天数据缺失。
- **相关代码**：`…/service/stat/StatService.java:47`、`…/service/stat/StatService.java:71`

## D-018 限流：Redis + Lua 的滑动窗口，多维度一次判断

- **背景**：登录、注册、点赞、评论、发文、搜索都需要防刷；多实例下限流状态必须共享。
- **方案**：`@RateLimit(name, dims = {USER, IP})` 注解 + 切面；Lua 脚本用 ZSet 记录窗口内每次请求的时间戳，先检查所有维度、全部通过再写入。规则可在配置 `app.rate-limit.*` 里覆盖。超限返回 HTTP 429。
- **备选**：固定窗口计数（INCR + EXPIRE，窗口边界处可以放过 2 倍请求）；令牌桶（平滑，但实现复杂，突发控制不直观）；Guava RateLimiter（单机）。
- **为什么选它**：滑动窗口精确，ZSet 实现简单直观；"先查后写"避免一个维度被拒、另一个维度白白计数。
- **代价与风险**：每次请求在 ZSet 里存一个成员，窗口内请求很多时（比如 1 分钟 10 万次）内存会上去，所以只用在低频接口上。
- **相关代码**：`…/core/ratelimit/RateLimitAspect.java:43`、`backend/code-nest-core/src/main/resources/scripts/sliding_window.lua`

## D-019 敏感词：DFA 前缀树，三个级别

- **背景**：发文时要检查敏感词，词库会被管理员修改。
- **方案**：启动时从库里加载词，构建前缀树；匹配时跳过空格和常见分隔符（防止"违 禁 词"绕过），最长匹配。三个级别：BLOCK 拒绝发布，REVIEW 进入人工审核（PENDING），REPLACE 替换成星号。管理端修改词库后通过 Redis 广播，各实例重建前缀树。
- **备选**：逐个 `String.contains`（词多时 O(词数 × 文本长度)）；Aho-Corasick 自动机（更快，但实现复杂）；正则。
- **为什么选它**：前缀树的匹配复杂度和词库大小无关，几千个词也是毫秒级；代码量小，能读懂。
- **代价与风险**：拼音、谐音、拆字等变体识别不了。
- **相关代码**：`…/core/trie/SensitiveTrie.java:50`

## D-020 搜索：ES + IK 分词，ES 不可用时降级到 MySQL LIKE

- **背景**：中文全文搜索需要分词；ES 很吃内存，用户的电脑不一定能常开。
- **方案**：ES 放在 Compose 的 `search` profile，`SEARCH_ENABLED=true` 时启用；索引用 IK 分词（`ik_max_word` 建索引、`ik_smart` 查询），标题权重 3、摘要 2、正文 1，返回高亮片段。文章发布/修改/删除通过 MQ 同步到 ES。ES 关闭或查询报错时自动降级成 MySQL `LIKE`，并在 Java 里生成同样格式的 `<em>` 高亮。
- **备选**：MySQL 全文索引 + ngram 分词（效果差，不能高亮）。
- **为什么选它**：ES 是面试高频考点；降级保证没有 ES 也能演示完整功能。
- **代价与风险**：IK 插件要和 ES 版本严格一致（见 P-006）；`LIKE '%词%'` 不走索引，数据量大时很慢，只作为兜底。
- **相关代码**：`…/service/search/SearchService.java:49`、`backend/code-nest-service/src/main/resources/es/article-mapping.json`

## D-021 定时任务：Redisson 分布式锁，拿不到锁就跳过

- **背景**：双实例部署时，计数写回、热榜计算、归档、对账只能有一个实例执行。
- **方案**：每个任务开始前 `tryLock()`（不等待），拿不到锁直接跳过本轮；Redisson 的看门狗在任务执行期间自动续期。低频任务拿到锁后再 `SET NX` 一个带过期时间的"本周期已执行"标记，防止两个实例先后各跑一次（P-018）。
- **备选**：XXL-Job / ElasticJob（多一个调度中心）；ShedLock（需要一张锁表，它的 lockAtLeastFor 就是这里"最短间隔"的来源）。
- **为什么选它**：Redis 已经在用，Redisson 只作为锁的实现，不引入新组件。
- **代价与风险**：Redis 主从切换时锁可能丢失，两个实例同时执行；本项目所有任务都是幂等的（写绝对值、覆盖写），重复执行无害。
- **相关代码**：`…/core/lock/JobLock.java`、`…/web/job/ScheduledJobs.java:28`

## D-022 未读数推送用 SSE，跨实例用 Redis Pub/Sub

- **背景**：页头的未读角标要实时更新；双实例时用户的长连接可能在另一台机器上。
- **方案**：`GET /api/notifications/stream` 返回 `SseEmitter`，每个实例在内存里保存连到本机的连接；新通知写库后向 Redis `notify:push` 频道发布"用户 ID + 未读数"，每个实例收到后推给自己持有的连接。每 25 秒发一次心跳注释行。前端用 fetch-event-source 带上 Authorization 头。
- **备选**：WebSocket（双向，本场景只需要服务端推送）；轮询（简单，但请求量大、有延迟）。
- **为什么选它**：SSE 基于普通 HTTP，Nginx 只需关闭缓冲；浏览器断线自动重连。
- **代价与风险**：每个连接占一个 Tomcat 异步上下文；Nginx 必须 `proxy_buffering off`，否则推送会被攒着（见 `deploy/nginx/nginx.conf`）。
- **相关代码**：`…/web/sse/SseHub.java:50`、`frontend/src/stores/notify.ts`

## D-023 前端：令牌放 localStorage，401 时单飞刷新

- **背景**：access token 30 分钟过期，用户不应该因此被踢下线。
- **方案**：axios 响应拦截器遇到 401 且错误码是"令牌过期/未登录"时，用 refresh token 换新令牌后重放原请求；多个请求同时 401 只发一次刷新（共享同一个 Promise）。刷新失败才跳登录页。
- **备选**：每个请求前检查过期时间（时钟不同步时不可靠）；HttpOnly Cookie 存令牌（防 XSS 窃取，但要处理 CSRF）。
- **为什么选它**：实现简单；单飞避免了"两个请求同时用同一个 refresh token，后端轮换后第二个必然失败"。
- **代价与风险**：localStorage 可以被 XSS 读取，所以渲染用户内容一律经过 DOMPurify（Markdown、搜索高亮）。
- **相关代码**：`frontend/src/api/http.ts`

## D-024 编辑器依赖本地打包，不走 CDN

- **背景**：md-editor-v3 默认从 unpkg 动态加载 highlight.js、screenfull、cropperjs，国内网络和内网经常加载失败。
- **方案**：npm 安装这三个库，通过 `config({ editorExtensions })` 传入实例，样式用 Vite 的 `?url` 导入；mermaid、katex、prettier、echarts 代码块用不到，直接关闭。
- **备选**：保持默认（依赖外网）；自建 CDN 镜像。
- **为什么选它**：离线也能完整使用，构建产物自包含。
- **代价与风险**：打包体积增加约 1MB（gzip 后约 300KB），只在详情页和编辑器按需加载。
- **相关代码**：`frontend/src/utils/mdEditor.ts`

## D-025 造数：JdbcTemplate 批量插入 + 聚合 SQL 生成计数

- **背景**：SPEC 14.1 要求 1 万用户、5 万文章、30 万关注关系等，用于压测。
- **方案**：`datagen` profile 下的 `CommandLineRunner`；固定随机种子，每次数据分布相同；用 `JdbcTemplate.batchUpdate` 每 2000 行一批，连接参数 `rewriteBatchedStatements=true` 让驱动改写成多值 INSERT；互动按幂律分布集中在少数文章上；article_stat、user_stat 最后用聚合 SQL 从明细表一次算出；然后预热活跃榜、热门榜，并在启用搜索时重建 ES 索引。
- **备选**：调业务接口造数（慢几个数量级，还会触发限流和 MQ）；MyBatis 逐条插入。
- **为什么选它**：本机实测全量造数约 45 秒。
- **代价与风险**：绕过了业务逻辑，所以 Feed 收件箱、Redis 计数等需要由读请求冷启动；不会产生通知。
- **相关代码**：`…/web/datagen/DataGenerator.java:49`

## D-026 压测计划由脚本生成

- **背景**：JMeter 的 .jmx 是冗长的 XML，手写、手改都容易出错，也不好审阅。
- **方案**：`perf/tools/gen_plans.py` 生成 `perf/plans/*.jmx`，所有可变参数（线程数、时长、主机、数据目录）都通过 `-J` 传入；`perf/run.sh` 负责切换策略、跑多轮、取中位数、汇总成表格。场景 4（大 V 发文的扇出耗时）不是吞吐类测试，直接用脚本计时。
- **备选**：用 JMeter GUI 录制保存。
- **为什么选它**：计划可以代码审阅、可复现；跑法统一。
- **代价与风险**：改计划要改 Python 脚本再重新生成。
- **相关代码**：`perf/tools/gen_plans.py`、`perf/run.sh`

## D-027 正式名称定为 code-nest

- **背景**：D-002 的占位名需要替换成正式名称；用户确认 GitHub 用户名为 `qq3939`，并采纳 `code-nest`（码巢）。
- **方案**：执行 `bash scripts/rename-project.sh code-nest qq3939`：模块目录 `backend/code-nest-*`、包名 `io.github.qq3939.codenest`、启动类 `CodeNestApplication`、数据库 `code_nest`、Redis 前缀和 MinIO 桶名同步更改。
- **备选**：单词形式 `codenest`——启动类会变成 `Codenest`，可读性差。
- **为什么选它**：两个单词用连字符分隔，脚本能自然生成 `CodeNest`、`code_nest`、`codenest` 三种形式；名字短、和"开发者聚集写作"的定位相符。
- **代价与风险**：已有的 Docker 数据卷里还是旧库名和旧桶名，需要 `docker compose down -v` 后重建；GitHub 仓库也要改名为 `code-nest`。
- **相关代码**：`scripts/rename-project.sh`
