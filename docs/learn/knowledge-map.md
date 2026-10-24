# 知识地图：后端八股 ↔ 本项目代码

面试回答技术问题，标准的顺序是"讲原理 → 联系本项目 → 说明局限"。这份地图负责中间那一步：每个八股主题在本项目的哪里出现、学习文档的哪一条讲过、原理去哪里看。

- **项目里的位置**：写法和其他学习文档一样，是 `` `路径:行号`（`锚点`） ``（见 [README](README.md)）。写着"项目里没有"的，面试时只讲原理，不要硬编项目里的例子。
- **学习文档**：讲过这个点的条目，例如"M2 第 10 条"指 [M2.md](M2.md) 知识点清单的第 10 条；P-xxx 在 [pitfalls.md](../pitfalls.md)，D-xxx 在 [decisions.md](../decisions.md)。
- **外部资料**：小林 = 小林 coding 图解系列，JG = JavaGuide，二哥 = 二哥的面渣逆袭。先看原理，再回到代码对照。
- **面试卡**：按"原理 → 本项目 → 局限"录好一张卡，就把 `[ ]` 改成 `[x]`。

## 1. Java 基础

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 面向对象：封装、继承、多态 | 多态：`service/count/CountService.java:13`（`CountService`）有三个实现，调用方只认接口；继承：`service/article/entity/ArticleDO.java:15`（`BaseDO`，实体共用 ID 和创建、更新时间） | M2 第 9 条 | [JG](https://javaguide.cn/java/basis/java-basic-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javase.html) | [ ] |
| `==` 与 `equals` | `service/cache/CacheService.java:114`（`LOCAL_NULL`，Caffeine 不能存 null，用一个哨兵对象代替，故意用 `==` 比引用）；`:107`（`CacheMode.NONE`，枚举每个值只有一个实例，可以用 `==`）；`service/stat/StatService.java:51`（`added`，`Long` 和 `int` 比较会自动拆箱；两个 `Long` 用 `==` 比的是引用，超出 -128～127 的缓存范围就不相等） | — | [JG](https://javaguide.cn/java/basis/java-basic-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javase.html) | [ ] |
| String 不可变 | 项目里没有 | — | [JG](https://javaguide.cn/java/basis/java-basic-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javase.html) | [ ] |
| 异常体系 | `core/exception/BizException.java:8`（`BizException`，继承 `RuntimeException`，业务代码不用层层 `throws`）；`web/handler/GlobalExceptionHandler.java:31`（`handleBiz`）；受检异常默认不回滚事务：`service/interaction/LikeService.java:56`（`rollbackFor`） | M0 第 2、3 条，M1 第 11 条 | [JG](https://javaguide.cn/java/basis/java-basic-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javase.html) | [ ] |
| 泛型擦除 | `service/article/ArticleQueryService.java:111`（`constructCollectionType`）：运行时 `List<ArticleListItemVO>` 只剩 `List`，Jackson 反序列化时要显式给出元素类型；所以 `service/cache/CacheService.java:105`（`get`）的参数是 `JavaType` 而不是 `Class<T>` | — | [JG](https://javaguide.cn/java/basis/java-basic-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javase.html) | [ ] |
| 反射与注解 | `core/ratelimit/RateLimit.java:13`（`RetentionPolicy.RUNTIME`，注解保留到运行时，切面才能通过反射读到）；Spring 创建 Bean、MyBatis 把结果集映射成对象都靠反射 | M1 第 11 条 | [JG](https://javaguide.cn/java/basis/java-basic-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javase.html) | [ ] |

## 2. 集合

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| ArrayList 与 LinkedList | 项目里没有 LinkedList；列表都是 `ArrayList`（以按下标读和尾部追加为主） | — | [JG](https://javaguide.cn/java/collection/java-collection-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/collection.html) | [ ] |
| HashMap 的结构、冲突、扩容 | 项目里没有直接相关的代码；用到的变体：`service/feed/FeedMerger.java:30`（`LinkedHashMap`，归并时按文章 ID 去重并保持顺序）、`service/count/RoutingCountService.java:21`（`EnumMap`，键是枚举时内部就是一个数组） | M3 第 9 条 | [JG](https://javaguide.cn/java/collection/java-collection-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/collection.html) | [ ] |
| HashMap 为什么线程不安全；ConcurrentHashMap 的思路 | `web/sse/SseHub.java:37`（`connections`，多个线程同时读写）、`:54`（`compute`，复合操作要放进原子方法）；P-026 | M3 第 12 条 | [JG](https://javaguide.cn/java/collection/java-collection-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/collection.html) | [ ] |

## 3. 并发

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 线程状态 | `IT/CountConcurrencyIT.java:66`（`CountDownLatch`）：200 个线程先在 `start.await()` 上等待（WAITING），`countDown()` 后同时开跑 | M2 关键代码导读第 11 条 | [JG](https://javaguide.cn/java/concurrent/java-concurrent-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javathread.html) | [ ] |
| synchronized 与锁升级 | 项目里没有：本机并发靠 `volatile`、`ConcurrentHashMap`，跨实例靠 Redis 锁 | — | [JG](https://javaguide.cn/java/concurrent/java-concurrent-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javathread.html) | [ ] |
| volatile | `core/config/RuntimeStrategy.java:14`（`volatile`）、`service/sensitive/SensitiveWordService.java:42`（`trie`，整体替换不可变的树） | M4 第 11 条，M5 第 6 条 | [JG](https://javaguide.cn/java/concurrent/java-concurrent-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javathread.html) | [ ] |
| CAS | 项目里没有直接用 `Atomic*`；同样"比较后再设置"的思路：`service/interaction/mapper/LikeRecordMapper.java:11`（`switchStatus`，`WHERE status = 旧值`）、`service/cache/CacheService.java:134`（`setIfAbsent`，Redis 的 `SET NX`） | M2 第 1 条 | [JG](https://javaguide.cn/java/concurrent/java-concurrent-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javathread.html) | [ ] |
| AQS 的思路 | 项目里没有直接用；测试里的 `CountDownLatch` 就是基于 AQS 实现的：`IT/CountConcurrencyIT.java:66`（`CountDownLatch`） | — | [JG](https://javaguide.cn/java/concurrent/java-concurrent-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javathread.html) | [ ] |
| 线程池原理 | `service/config/AsyncConfig.java:29`（`build`，核心数、最大数、有界队列、拒绝策略） | M3 第 11 条 | [JG](https://javaguide.cn/java/concurrent/java-concurrent-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javathread.html) | [ ] |
| ThreadLocal | `core/security/UserContext.java:11`（`HOLDER`）、`web/interceptor/AuthInterceptor.java:64`（`afterConcurrentHandlingStarted`，异步请求也要清理）、`service/config/AsyncConfig.java:44`（`mdcPropagation`，不会自动跨线程，要手动传）；P-004 | M0 第 4 条，M1 面试题 8，M3 第 11 条 | [JG](https://javaguide.cn/java/concurrent/java-concurrent-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/javathread.html) | [ ] |

## 4. JVM

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 内存区域、对象创建 | 项目里没有 | — | [JG](https://javaguide.cn/java/jvm/memory-area.html)、[二哥](https://javabetter.cn/sidebar/sanfene/jvm.html) | [ ] |
| 可达性分析、GC 算法与收集器 | 项目里没有直接代码；堆大小：`docker-compose.yml:122`（`JAVA_OPTS`，容器里的后端 `-Xmx768m`），压测时本机跑 `-Xmx1g`；JDK 17 在至少 2 核、1792 MB 内存的机器上默认用 G1，更小的机器（比如限了资源的容器）会退回 Serial | M6 第 3 条 | [JG](https://javaguide.cn/java/jvm/jvm-garbage-collection.html)、[二哥](https://javabetter.cn/sidebar/sanfene/jvm.html) | [ ] |
| 类加载与双亲委派 | 项目里没有直接代码；相关的坑：P-014（Spring Boot 的 fat jar 由自己的类加载器按需从 jar 里读类，覆盖正在运行的 jar 会报 `ClassNotFoundException`）、P-019（坏的类文件在加载时报 `ClassFormatError`） | — | [JG](https://javaguide.cn/java/jvm/classloader.html)、[二哥](https://javabetter.cn/sidebar/sanfene/jvm.html) | [ ] |
| OOM 排查 | 项目里没有遇到过；预防：`service/cache/CacheService.java:87`（`maximumSize`，本地缓存有上限）、`service/config/AsyncConfig.java:34`（`setQueueCapacity`，线程池队列有界） | M3 第 11 条 | [JG](https://javaguide.cn/java/jvm/memory-area.html)、[二哥](https://javabetter.cn/sidebar/sanfene/jvm.html) | [ ] |

## 5. 设计模式

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 单例 | Spring Bean 默认单例：`core/config/RuntimeStrategy.java:12`（`@Component`，全局只有一个实例，所以字段要考虑多线程可见性） | M5 第 6 条 | [JG](https://javaguide.cn/system-design/design-pattern.html)、[二哥](https://javabetter.cn/sidebar/sanfene/shejimoshi.html) | [ ] |
| 工厂 | 静态工厂方法：`core/trie/SensitiveTrie.java:29`（`build`）、`core/result/Result.java:11`（`ok`） | — | [JG](https://javaguide.cn/system-design/design-pattern.html)、[二哥](https://javabetter.cn/sidebar/sanfene/shejimoshi.html) | [ ] |
| 策略 | `service/count/RoutingCountService.java:25`（`RoutingCountService`） | M2 第 9 条 | [JG](https://javaguide.cn/system-design/design-pattern.html)、[二哥](https://javabetter.cn/sidebar/sanfene/shejimoshi.html) | [ ] |
| 代理 | `core/ratelimit/RateLimitAspect.java:42`（`@Around`）；`@Transactional` 也是代理 | M1 第 11 条 | [JG](https://javaguide.cn/system-design/design-pattern.html)、[二哥](https://javabetter.cn/sidebar/sanfene/shejimoshi.html) | [ ] |
| 模板方法 | `web/config/TraceIdFilter.java:23`（`OncePerRequestFilter`，父类定好流程，子类只写 `doFilterInternal`）；`core/mq/ConsumerSupport.java:28`（`handle`，ack / 重试 / 死信的流程固定，业务处理作为参数传进来） | M0 第 4 条，M3 第 3 条 | [JG](https://javaguide.cn/system-design/design-pattern.html)、[二哥](https://javabetter.cn/sidebar/sanfene/shejimoshi.html) | [ ] |
| 观察者 | Redis Pub/Sub：`service/cache/CacheService.java:97`（`subscribe`）、`service/sensitive/SensitiveWordService.java:45`（`init`）；事务提交后的回调：`service/common/AfterCommit.java:15`（`run`） | M2 第 2 条，M4 第 8 条 | [JG](https://javaguide.cn/system-design/design-pattern.html)、[二哥](https://javabetter.cn/sidebar/sanfene/shejimoshi.html) | [ ] |

## 6. Spring 与 MyBatis(-Plus)

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| IoC、DI | `web/CodeNestApplication.java:15`（`scanBasePackages`）、`service/outbox/OutboxPublisher.java:37`（`@Qualifier`）、`service/count/RoutingCountService.java:18`（`@Primary`） | M0 第 10 条 | [JG](https://javaguide.cn/system-design/framework/spring/spring-knowledge-and-questions-summary.html)、[二哥](https://javabetter.cn/sidebar/sanfene/spring.html) | [ ] |
| Bean 作用域 | 项目里的 Bean 都是默认的单例；有状态的单例要处理并发：`core/config/RuntimeStrategy.java:14`（`volatile`） | M5 第 6 条 | [JG](https://javaguide.cn/system-design/framework/spring/spring-knowledge-and-questions-summary.html)、[二哥](https://javabetter.cn/sidebar/sanfene/spring.html) | [ ] |
| AOP 与代理 | `core/ratelimit/RateLimitAspect.java:42`（`@Around`） | M1 第 11 条 | [JG](https://javaguide.cn/system-design/framework/spring/spring-knowledge-and-questions-summary.html)、[二哥](https://javabetter.cn/sidebar/sanfene/spring.html) | [ ] |
| 事务传播 | `service/outbox/OutboxService.java:37`（`MANDATORY`，没有外层事务就报错） | M3 第 1 条 | [JG](https://javaguide.cn/system-design/framework/spring/spring-knowledge-and-questions-summary.html)、[二哥](https://javabetter.cn/sidebar/sanfene/spring.html) | [ ] |
| 事务失效场景 | `service/interaction/LikeService.java:56`（`rollbackFor`）；本项目事务方法都是 public、由其他 Bean 调用 | M1 第 11 条、面试题 9 | [JG](https://javaguide.cn/system-design/framework/spring/spring-knowledge-and-questions-summary.html)、[二哥](https://javabetter.cn/sidebar/sanfene/spring.html) | [ ] |
| 自动配置 | `core/config/JacksonConfig.java:18`（`longToStringCustomizer`，在自动配置的 `ObjectMapper` 上追加，而不是替换） | M0 第 10 条、面试题 8 | [JG](https://javaguide.cn/system-design/framework/spring/spring-knowledge-and-questions-summary.html)、[二哥](https://javabetter.cn/sidebar/sanfene/spring.html) | [ ] |
| `#{}` 与 `${}` | `ArticleStatMapper.xml:6`（`column`，列名不能用 `#{}` 传，也不能用 `${}` 拼，用 `choose` 从白名单里选） | M2 第 4 条 | [JG](https://javaguide.cn/system-design/framework/mybatis/mybatis-interview.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mybatis.html) | [ ] |
| MyBatis 一级、二级缓存 | 项目里没有用，应用层的缓存是 Caffeine + Redis | M4 第 8 条 | [JG](https://javaguide.cn/system-design/framework/mybatis/mybatis-interview.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mybatis.html) | [ ] |
| 插件原理 | `service/config/MybatisPlusConfig.java:20`（`mybatisPlusInterceptor`，分页插件就是一个 MyBatis 插件：拦截 SQL 执行，自动加 `LIMIT` 并查总数） | — | [JG](https://javaguide.cn/system-design/framework/mybatis/mybatis-interview.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mybatis.html) | [ ] |

## 7. MySQL

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 索引：B+ 树、联合索引、最左前缀 | `V1__init_schema.sql:31`（`idx_status_publish`，首页按状态过滤再按发布时间排序）、`:32`（`idx_author_status_publish`，作者主页） | — | [小林](https://xiaolincoding.com/mysql/index/index_interview.html)、[JG](https://javaguide.cn/database/mysql/mysql-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mysql.html) | [ ] |
| 唯一索引兜底并发 | `V1__init_schema.sql:16`（`uk_username`）、`:93`（`uk_user_target`） | M1 第 8 条，M2 第 1 条 | [小林](https://xiaolincoding.com/mysql/index/index_interview.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mysql.html) | [ ] |
| 事务、隔离级别、MVCC | `service/count/NaiveCountService.java:31`（`selectById`，快照读） | M2 第 10 条 | [小林](https://xiaolincoding.com/mysql/transaction/mvcc.html)、[JG](https://javaguide.cn/database/mysql/innodb-implementation-of-mvcc.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mysql.html) | [ ] |
| 锁 | `ArticleStatMapper.xml:15`（`increase`，当前读，加行锁）；P-021（热点行上排队） | M2 第 10 条 | [小林](https://xiaolincoding.com/mysql/lock/how_to_lock.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mysql.html) | [ ] |
| 慢查询优化 | 深分页：`ArticleMapper.xml:59`（`selectPublishedAfterId`，按主键游标分批）；用不上索引：`ArticleMapper.xml:71`（`searchByLike`，`LIKE '%kw%'` 只能全表扫描） | M2 第 8 条，M4 第 3 条 | [JG](https://javaguide.cn/database/mysql/mysql-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mysql.html) | [ ] |
| redo log、undo log、binlog | 项目里没有直接代码；P-021：每次提交要刷两次盘（`innodb_flush_log_at_trx_commit=1`、`sync_binlog=1`），WSL2 上单条约 8ms | M2 第 10 条 | [小林](https://xiaolincoding.com/mysql/log/how_update.html)、[二哥](https://javabetter.cn/sidebar/sanfene/mysql.html) | [ ] |
| SQL 手写：分组统计 | `web/datagen/DataGenerator.java:348`（`stats`，`GROUP BY` 聚合重建计数表） | M6 第 2 条 | [JG](https://javaguide.cn/database/mysql/mysql-questions-01.html) | [ ] |
| SQL 手写：窗口函数取每组前 N | 项目里没有 | — | [JG](https://javaguide.cn/database/mysql/mysql-questions-01.html) | [ ] |

## 8. Redis

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 数据结构 | Hash + Set：`lua/count_incr.lua`；ZSet：`lua/rank_add.lua`、`lua/feed_push.lua`、`lua/sliding_window.lua`；HyperLogLog：`service/stat/StatService.java:50`（`recordVisit`）；String：`service/cache/CacheService.java:156`（`writeRedis`） | M2 第 5 条，M3 第 7 条，M4 第 1、9 条，M5 第 1 条 | [小林](https://xiaolincoding.com/redis/base/redis_interview.html)、[JG](https://javaguide.cn/database/redis/redis-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/redis.html) | [ ] |
| 持久化 | `docker-compose.yml:34`（`appendonly`，AOF，每秒刷一次盘）；D-007 | — | [小林](https://xiaolincoding.com/redis/storage/aof.html)、[二哥](https://javabetter.cn/sidebar/sanfene/redis.html) | [ ] |
| 过期与淘汰策略 | 缓存都带 TTL，还加了随机抖动：`service/cache/CacheService.java:161`（`jitter`）；项目没配 `maxmemory`，默认不限内存、不淘汰（`noeviction`） | M4 第 6 条 | [小林](https://xiaolincoding.com/redis/module/strategy.html)、[二哥](https://javabetter.cn/sidebar/sanfene/redis.html) | [ ] |
| 缓存穿透、击穿、雪崩 | `service/cache/CacheService.java:156`（`writeRedis`，空值缓存）、`:125`（`readRedisOrRebuild`，互斥重建）、`:170`（`getLogical`，逻辑过期） | M4 第 4～6 条 | [小林](https://xiaolincoding.com/redis/base/redis_interview.html)、[二哥](https://javabetter.cn/sidebar/sanfene/redis.html) | [ ] |
| 缓存一致性 | `service/cache/CacheService.java:231`（`evict`，提交后删缓存，失败走 MQ 补偿） | M4 第 7 条 | [小林](https://xiaolincoding.com/redis/architecture/mysql_redis_consistency.html)、[二哥](https://javabetter.cn/sidebar/sanfene/redis.html) | [ ] |
| 两级缓存 | `service/cache/CacheService.java:105`（`get`）、`:97`（`subscribe`，失效广播） | M4 第 8 条，M6 第 6 条 | [小林](https://xiaolincoding.com/redis/architecture/mysql_redis_consistency.html) | [ ] |
| 分布式锁 | `service/cache/CacheService.java:134`（`setIfAbsent`，带令牌和过期时间）、`:146`（`unlockScript`，比较令牌后再删，不会删掉别人的锁）、`core/lock/JobLock.java:37`（`tryLock`，Redisson）；P-018 | M4 第 5 条，M6 第 8 条 | [JG](https://javaguide.cn/database/redis/redis-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/redis.html) | [ ] |
| 为什么快 | pipeline 把多条命令合成一次往返：`service/count/RedisBatchCountService.java:82`（`executePipelined`）；Lua 脚本在服务端一次执行完：`lua/count_incr.lua`；单线程 + IO 多路复用：项目里没有代码 | M2 第 5 条 | [小林](https://xiaolincoding.com/redis/base/redis_interview.html)、[JG](https://javaguide.cn/database/redis/redis-questions-01.html) | [ ] |

## 9. 消息队列

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 为什么用 MQ | `service/outbox/OutboxService.java:38`（`save`，业务写库和"要发的消息"在同一个事务里，之后异步投递，通知、Feed、搜索、排行互不影响） | M3 第 1 条 | [JG](https://javaguide.cn/high-performance/message-queue/message-queue.html)、[二哥](https://javabetter.cn/sidebar/sanfene/rocketmq.html) | [ ] |
| RabbitMQ 交换机类型 | `core/mq/RabbitTopologyConfig.java:30`（`rabbitTopology`，业务事件走 topic 交换机，重试和死信走 direct 交换机） | M3 第 3 条 | [JG](https://javaguide.cn/high-performance/message-queue/rabbitmq-questions.html) | [ ] |
| 怎么保证不丢 | `service/outbox/OutboxPublisher.java:76`（`send`，持久化消息 + 发布确认）、`application.yml:52`（`acknowledge-mode`，消费端手动 ack） | M3 第 2 条 | [JG](https://javaguide.cn/high-performance/message-queue/rabbitmq-questions.html) | [ ] |
| 重复消费与幂等 | `V1__init_schema.sql:152`（`uk_msg_id`）、`service/notify/NotifyService.java:45`（`handle`） | M3 第 4 条 | [JG](https://javaguide.cn/high-performance/message-queue/message-queue.html) | [ ] |
| 死信和延迟 | `core/mq/ConsumerSupport.java:28`（`handle`，失败后进 TTL 重试队列，次数用完进死信） | M3 第 3 条，M5 第 5 条 | [JG](https://javaguide.cn/high-performance/message-queue/rabbitmq-questions.html) | [ ] |
| 消息积压 | 项目里没有遇到；相关配置：`application.yml:53`（`prefetch`，每个消费者最多预取 20 条）、`:54`（`concurrency`，消费者线程数） | — | [JG](https://javaguide.cn/high-performance/message-queue/message-queue.html) | [ ] |
| Kafka、RocketMQ、RabbitMQ 的区别 | 项目里没有 Kafka 和 RocketMQ；为什么用 outbox 而不用 RocketMQ 事务消息：D-011 | M3 面试题 1 | [JG](https://javaguide.cn/high-performance/message-queue/kafka-questions-01.html)、[二哥](https://javabetter.cn/sidebar/sanfene/rocketmq.html) | [ ] |

## 10. Elasticsearch

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 倒排索引 | 项目里没有直接代码（是 ES 内部的实现） | M4 第 3 条 | [Elastic 权威指南](https://www.elastic.co/guide/cn/elasticsearch/guide/current/inverted-index.html)、[JG（付费）](https://javaguide.cn/database/elasticsearch/elasticsearch-questions-01.html) | [ ] |
| 分词 | `es/article-mapping.json:4`（`ik_max_word`，建索引时切细、提高召回，搜索时用 `ik_smart` 切粗） | M4 第 3 条 | [Elastic 权威指南](https://www.elastic.co/guide/cn/elasticsearch/guide/current/inverted-index.html) | [ ] |
| 为什么不用 LIKE | `ArticleMapper.xml:71`（`searchByLike`）；本机对比：词序颠倒的"击穿缓存"、只在正文里出现的词，`LIKE` 都搜不到 | M4 第 3 条 | [Elastic 权威指南](https://www.elastic.co/guide/cn/elasticsearch/guide/current/inverted-index.html) | [ ] |
| 和 MySQL 怎么同步 | `service/search/SearchIndexService.java:63`（`upsert`，消息驱动单篇更新）、`:86`（`reindexAll`，全量重建）；P-022 | M4 第 3 条 | [JG（付费）](https://javaguide.cn/database/elasticsearch/elasticsearch-questions-01.html) | [ ] |

## 11. 网络

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| TCP 三次握手与四次挥手 | 项目里没有直接代码；建连有成本：P-017（不开连接池时每次 pipeline 都新建 TCP 连接，高并发下报 `Connection reset`）；`deploy/nginx/nginx.conf:10`（`keepalive`，Nginx 和后端之间复用连接） | M6 第 5 条 | [小林](https://xiaolincoding.com/network/3_tcp/tcp_interview.html)、[二哥](https://javabetter.cn/sidebar/sanfene/network.html) | [ ] |
| 可靠传输、TCP 与 UDP | 项目里没有 | — | [小林](https://xiaolincoding.com/network/)、[二哥](https://javabetter.cn/sidebar/sanfene/network.html) | [ ] |
| HTTP 与 HTTPS | 项目只用 HTTP，没有配 HTTPS；SSE 就是一个一直不结束的 HTTP 响应：`deploy/nginx/nginx.conf:33`（`proxy_buffering`，关掉缓冲才能实时转发） | M3 第 6 条 | [小林](https://xiaolincoding.com/network/2_http/http_interview.html)、[二哥](https://javabetter.cn/sidebar/sanfene/network.html) | [ ] |
| DNS | `deploy/nginx/nginx.conf:5`（`resolver`，向 Docker 内置 DNS 解析服务名，每 10 秒重新解析）；P-023 | — | [小林](https://xiaolincoding.com/network/1_base/what_happen_url.html) | [ ] |
| 输入 URL 之后发生了什么 | 本项目里的一次请求：浏览器 → Nginx（8080）→ 静态页面，或经 `/api` 反向代理到后端（8081）→ MySQL / Redis，见 [README 的架构图](../../README.md#架构) | — | [小林](https://xiaolincoding.com/network/1_base/what_happen_url.html) | [ ] |

## 12. 操作系统

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| 进程与线程 | 后端是一个 JVM 进程，里面有 Tomcat 的请求线程（`http-nio-…`）、线程池线程（`outbox-…`）、定时任务线程等：`service/config/AsyncConfig.java:29`（`build`） | M3 第 11 条 | [小林](https://xiaolincoding.com/os/4_process/process_base.html)、[二哥](https://javabetter.cn/sidebar/sanfene/os.html) | [ ] |
| 上下文切换 | 项目里没有 | — | [小林](https://xiaolincoding.com/os/4_process/process_base.html)、[二哥](https://javabetter.cn/sidebar/sanfene/os.html) | [ ] |
| 死锁条件 | 项目里没有遇到；Redis 锁都带过期时间，持有者挂了锁也会自动释放：`service/cache/CacheService.java:134`（`setIfAbsent`） | M4 第 5 条 | [小林](https://xiaolincoding.com/os/4_process/deadlock.html)、[二哥](https://javabetter.cn/sidebar/sanfene/os.html) | [ ] |
| 虚拟内存与分页 | 项目里没有直接代码；ES 用 mmap 读索引文件，要求 `vm.max_map_count` 至少 262144（P-008） | — | [小林](https://xiaolincoding.com/os/3_memory/vmem.html)、[二哥](https://javabetter.cn/sidebar/sanfene/os.html) | [ ] |
| IO 模型 | 项目里没有直接代码；Tomcat 的 NIO 连接器、Lettuce（基于 Netty）和 Redis 服务端用的都是 IO 多路复用 | — | [小林](https://xiaolincoding.com/os/8_network_system/selete_poll_epoll.html)、[二哥](https://javabetter.cn/sidebar/sanfene/os.html) | [ ] |

## 13. 场景题

| 主题 | 项目里的位置 | 学习文档 | 外部资料 | 面试卡 |
|---|---|---|---|---|
| Feed 推还是拉 | `service/feed/FeedService.java:248`（`onPublished`，推）、`:98`（`sourceKeys`，各模式读哪些 ZSet）；D-010 | M3 第 7～9 条 | — | [ ] |
| 计数怎么保证准确 | `service/count/RedisBatchCountService.java:60`（`increase`）、`:110`（`flush`）、`:145`（`reconcile`） | M2 第 3～7、10 条 | — | [ ] |
| 排行榜 | `service/rank/RankService.java:79`（`addActiveScore`）、`:122`（`refreshHotArticles`） | M4 第 1、2 条 | — | [ ] |
| 两级缓存的一致性 | `service/cache/CacheService.java:231`（`evict`）、`:97`（`subscribe`） | M4 第 7、8 条 | [小林](https://xiaolincoding.com/redis/architecture/mysql_redis_consistency.html) | [ ] |
| 消息丢失或重复 | `service/outbox/OutboxPublisher.java:49`（`relayDue`）、`core/mq/ConsumerSupport.java:28`（`handle`） | M3 第 1～4 条 | [JG](https://javaguide.cn/high-performance/message-queue/message-queue.html) | [ ] |
| 限流 | `core/ratelimit/RateLimitAspect.java:43`（`around`）、`lua/sliding_window.lua`；D-018 | M4 第 9 条 | — | [ ] |
