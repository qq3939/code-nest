# 踩坑记录

开发中遇到的问题，编号依次递增。

## P-000 模板（复制后填写）

- **现象**：
- **原因**：
- **解决**：
- **教训**：
- **相关提交**：

---

## P-001 前端拿到的 ID 末尾几位变成 0

- **现象**：文章 ID `2113114236481294337` 到了前端变成 `2113114236481294300`，用它请求详情返回 404。
- **原因**：雪花 ID 是 64 位整数，JavaScript 的 Number 是双精度浮点，只能精确表示 2^53 以内的整数，`JSON.parse` 时就已经丢了精度。
- **解决**：Jackson 全局把 `Long` / `long` 序列化成字符串；前端的类型定义里所有 ID 都是 `string`。
- **教训**：前后端约定 ID 类型时要考虑 JS 的数值范围；用集成测试断言 `id` 字段是字符串（`AuthIT.registerLoginAndMe`）。
- **相关提交**：a084d5f（`JacksonConfig`）

## P-002 publisher confirm 的回调方法名写错

- **现象**：编译报错，找不到 `confirm.ack()`。
- **原因**：Spring AMQP 的 `CorrelationData.Confirm` 是普通类，方法是 `isAck()`、`getReason()`，不是 record 风格的访问器。
- **解决**：改用 `isAck()` / `getReason()`。另外要同时检查 `correlation.getReturned()`：消息到了交换机但没有队列绑定时，confirm 仍然是 ack，只有 return 回调能发现"不可路由"。
- **教训**：confirm 只表示 Broker 收到了消息，不表示消息进了队列。
- **相关提交**：a084d5f（`OutboxPublisher`）

## P-003 刚写进去的时间读出来对不上

- **现象**：Feed 游标用"毫秒时间戳"定位，偶尔重复返回同一篇文章。
- **原因**：`LocalDateTime.now()` 有微秒甚至纳秒精度，而列是 `DATETIME(3)`。MySQL 存入时对多出来的位数**四舍五入**，于是内存里的值（.1234567）和库里的值（.123）对不上，Redis 里用内存值算的 score 比库里大 1 毫秒。
- **解决**：所有写库的时间统一用 `Times.now()`，先截断到毫秒（`truncatedTo(MILLIS)`）；MyBatis-Plus 的自动填充也用它。
- **教训**：同一个时间会在 Java、MySQL、Redis 三处出现时，要先统一精度。
- **相关提交**：a084d5f（`…/core/util/Times.java:20`）

## P-004 SSE 请求结束后 ThreadLocal 里的用户串到别的请求

- **现象**：（写 SSE 接口时排查出的隐患）SSE 长连接建立后，同一个 Tomcat 线程处理的下一个请求可能拿到上一个用户的身份；另外连接结束时的异步再派发（ASYNC）会因为令牌已过期被拦截器拒绝，日志里出现无意义的 401。
- **原因**：SSE 是异步请求：Controller 返回 `SseEmitter` 后，请求进入异步模式，`HandlerInterceptor.afterCompletion` 不会立刻执行，`UserContext` 的 ThreadLocal 没有被清理，而线程已经回到线程池。
- **解决**：拦截器实现 `AsyncHandlerInterceptor`，在 `afterConcurrentHandlingStarted` 里也清理 ThreadLocal；异步分派（`DispatcherType.ASYNC`）时跳过鉴权，避免重复解析。
- **教训**：用 ThreadLocal 传上下文时，必须确认每一条请求路径（同步、异步、异常）都会清理。
- **相关提交**：a084d5f（`…/web/interceptor/AuthInterceptor.java`）

## P-005 Docker Hub 上的 minio/minio 镜像拉不到新版本

- **现象**：`docker compose pull` 拉 `minio/minio:latest` 得到的是很旧的版本，或者在部分镜像源上直接失败。
- **原因**：MinIO 官方在 2025 年停止在 Docker Hub 发布社区版镜像。
- **解决**：改用 `bitnamilegacy/minio:2025.4.22`（Bitnami 的归档仓库，自带 `mc` 客户端，健康检查可以直接用 `curl`）。数据目录变成 `/bitnami/minio/data`，建桶交给单独的 `minio-init` 容器（见 P-007）。
- **教训**：锁定镜像版本号，不要用 `latest`；记录镜像来源。
- **相关提交**：a084d5f（`docker-compose.yml`）

## P-006 Elasticsearch 的 IK 分词插件下载失败

- **现象**：构建 `deploy/elasticsearch/Dockerfile` 时 `elasticsearch-plugin install` 报 403 或超时。
- **原因**：IK 插件从 `release.infinilabs.com` 下载，这个地址在部分网络环境下不可达；另外 IK 的版本必须和 ES **完全一致**（8.19.5 只能装 8.19.5 的 IK）。
- **解决**：Dockerfile 用 `ARG ES_VERSION` 同时控制两者版本；网络不通时可以先在浏览器里把 zip 下载到 `deploy/elasticsearch/`，改成 `install file:///…` 本地安装。搜索默认关闭（`SEARCH_ENABLED=false`），没有 ES 也能用 MySQL 降级搜索。
- **教训**：外部依赖要有降级方案；在开发容器里无法验证 IK，这一步只能在用户机器上验证。
- **相关提交**：a084d5f（`deploy/elasticsearch/Dockerfile`）

## P-007 MinIO 初始化脚本偶尔建桶失败

- **现象**：`minio-init` 容器有时报 "Server not initialized"，桶没有建出来，上传图片 500。
- **原因**：Bitnami 的 MinIO 镜像首次启动时会自己初始化一遍然后重启服务，健康检查在重启前就已经通过了，`mc mb` 正好撞上重启。
- **解决**：`deploy/minio/init-bucket.sh` 里加重试循环，`mc mb --ignore-existing` 成功并设置匿名下载策略后才退出。
- **教训**：健康检查通过 ≠ 服务完全就绪；初始化脚本要幂等、可重试。
- **相关提交**：a084d5f

## P-008 Windows 上 Elasticsearch 启动后马上退出

- **现象**：ES 容器日志里出现 `max virtual memory areas vm.max_map_count [65530] is too low`。
- **原因**：ES 要求 `vm.max_map_count ≥ 262144`，Docker Desktop 的 WSL2 虚拟机默认是 65530。
- **解决**：在 PowerShell 里执行 `wsl -d docker-desktop sysctl -w vm.max_map_count=262144`（重启后失效）；永久生效需要在 `%USERPROFILE%\.wslconfig` 里配置 `kernelCommandLine = "sysctl.vm.max_map_count=262144"`，然后 `wsl --shutdown`。另外给 Docker Desktop 分配至少 6GB 内存。
- **教训**：README 的 Windows 注意事项里写清楚。
- **相关提交**：README

## P-009 Spring Data Redis 的 ZSet 范围查询签名对不上

- **现象**：Feed 多路归并时想在 pipeline 里用 `zRevRangeByScoreWithScores(key, Range, Limit)`，编译不过。
- **原因**：`StringRedisConnection` 上的重载和 `RedisZSetCommands` 上的不一样，`Range<String>` 版本在 String 连接上不可用。
- **解决**：用 `zRevRangeByScoreWithScores(key, min, max, offset, count)` 这个 double 版本；`min` 取 1 而不是 0，顺便把 score 为 0 的占位成员（用来区分"空收件箱"和"收件箱不存在"）排除掉。
- **教训**：pipeline 里只能用 connection 级别的 API，和 `RedisTemplate.opsForZSet()` 不完全一样。
- **相关提交**：a084d5f（`…/service/feed/FeedService.java:126`）

## P-010 列名或别名用了 `reads`，SQL 语法错误

- **现象**：`daily_stat` 建表失败；造数工具在汇总 user_stat 时报 `You have an error in your SQL syntax … near 'reads'`。
- **原因**：`READS` 是 MySQL 的保留字（存储过程的 `READS SQL DATA`）。
- **解决**：建表时列名加反引号 `` `reads` ``；造数 SQL 的别名改成 `read_sum`。
- **教训**：起列名、别名前查一下 MySQL 保留字表，或者干脆避开常见英文单词。
- **相关提交**：a084d5f（V1 迁移脚本）、本次提交（`DataGenerator`）

## P-011 缓存重建锁的 key 和数据 key 是同一个

- **现象**：两级缓存的集成测试里，`get` 读到一个 UUID 字符串，JSON 解析失败。
- **原因**：重建锁的 key 原来是用 `key.replace("project-a:cache:", "project-a:lock:cache:")` 算出来的。测试用的 key 不以 `cache:` 开头，`replace` 没有替换任何内容，锁 key 就等于数据 key，`SET NX` 把锁的令牌（UUID）写进了数据 key。业务代码里的 key 恰好都以 `cache:` 开头，所以平时没暴露。
- **解决**：改成 `lockKeyOf(key)`：去掉前缀后统一加 `lock:`，对任何 key 都不会和原 key 相同。
- **教训**：字符串替换生成 key 时，要考虑"没匹配上"的情况；集成测试最好用和业务不同的 key 形态，能发现隐含假设。
- **相关提交**：31a46ac（`…/service/cache/CacheService.java:267`）

## P-012 浏览器不在东八区时"x 小时前"算错

- **现象**：在 UTC 时区的浏览器里，刚发的文章显示"7 小时内"（未来时间）。
- **原因**：后端统一用 `Asia/Shanghai` 的 `LocalDateTime`，序列化成不带时区的字符串 `2026-10-22T11:37:00.123`。dayjs 按浏览器本地时区解析，UTC 浏览器把它当成 UTC 11:37，比真实时间晚 8 小时。
- **解决**：前端用 dayjs 的 utc + timezone 插件，统一 `dayjs.tz(t, 'Asia/Shanghai')` 解析。
- **教训**：接口里的时间最好带时区（`OffsetDateTime` 或时间戳）；既然选了 `LocalDateTime`，就要在前端固定解析时区。
- **相关提交**：dd0108b（`frontend/src/utils/format.ts`）

## P-013 编辑器的代码高亮、全屏按钮没反应

- **现象**：详情页的代码块没有颜色，浏览器控制台有多条 `ERR_TUNNEL_CONNECTION_FAILED`。
- **原因**：md-editor-v3 默认从 unpkg CDN 动态加载 highlight.js、screenfull、cropperjs，网络不通就加载失败。
- **解决**：本地安装这几个库，用 `config({ editorExtensions })` 注入实例（见 D-024）。
- **教训**：上线前在断网环境下检查一遍控制台的网络请求。
- **相关提交**：dd0108b（`frontend/src/utils/mdEditor.ts`）

## P-014 运行中的后端突然报 `ClassNotFoundException: ch.qos.logback…`

- **现象**：本地 `java -jar target/app.jar` 跑着的后端，在执行一次 `mvn package` 之后开始报类找不到，请求卡住。
- **原因**：Spring Boot 的 fat jar 是运行时按需从 jar 里读取类的，`mvn package` 覆盖了正在使用的 jar 文件，JVM 再去读还没加载的类时就读到了新 jar 里错位的内容。
- **解决**：重新打包前先停掉后端；或者把 jar 复制一份再运行。
- **教训**：不要覆盖正在运行的 jar；Docker 部署不会遇到（镜像是不可变的）。
- **相关提交**：无代码改动

## P-015 压测准备脚本被登录限流挡住

- **现象**：`perf/prepare.sh` 需要登录 400 个造数用户拿令牌，登录到第 11 个开始全部返回 429。
- **原因**：登录接口的限流规则是每个 IP 每分钟 10 次，这是防撞库的正常设置。
- **解决**：新增 `perf` profile（`application-perf.yml`），只在压测时放宽登录、注册和搜索的限流，并把 access token 有效期延长到 12 小时；点赞的限流保持默认，因为场景 5 要测它。
- **教训**：压测环境的配置要和功能配置分开，用 profile 叠加，而不是改默认值。
- **相关提交**：本次提交

## P-016 集成测试里用 `@MockitoSpyBean` 会让消息被别的上下文消费

- **现象**：（写测试时预判到的问题）原本打算用 spy 让 `NotifyService` 抛异常来测死信，这样消息可能被另一个上下文正常消费掉，测试结果不稳定。
- **原因**：加了 `@MockitoSpyBean` 的测试类会创建一个新的 Spring 上下文，而 Spring 测试框架会缓存之前的上下文，旧上下文里的 `@RabbitListener` 仍然在监听同一个队列（容器是共享的），消息被它抢走。
- **解决**：不用 spy，改为发送一条 payload 本身就无法反序列化的消息（`articleId` 是非数字字符串），消费必然失败，走完重试进入死信。整个 IT 只用一个上下文。
- **教训**：多个测试上下文共享同一个 MQ 时，监听器会互相抢消息；尽量让所有集成测试共用一个上下文配置。
- **相关提交**：31a46ac（`MessageReliabilityIT`）

## P-017 压测时缓存场景出现 7%~12% 的 500，日志是 `Unable to connect to Redis`

- **现象**：场景 1（热点文章详情）50 并发跑起来后，部分请求返回 500，后端日志里有 `RedisConnectionFailureException: Unable to connect to Redis` 和 `Connection reset by peer`。低并发时完全正常。
- **原因**：Lettuce 默认所有命令共用一条长连接，但 `executePipelined` 和事务这类命令需要独占连接，没开连接池时每次调用都会新建一条 TCP 连接、用完关掉。高并发下短时间内建连/断连太多，超过了 Redis 和本机网络栈的承受能力。
- **解决**：引入 `commons-pool2`，在 `application.yml` 里打开 `spring.data.redis.lettuce.pool`（max-active 32、min-idle 4、max-wait 2s），独占连接从池里借、用完归还。重跑后错误率归零。
- **教训**：用到 pipeline/事务的项目要开连接池；功能测试发现不了这类问题，必须压测。
- **相关提交**：本次提交（`project-a-core/pom.xml`、`application.yml`）

## P-018 定时任务加了分布式锁，仍可能被两个实例先后各执行一次

- **现象**：做双实例演示、核对"定时任务只在一个实例执行"时发现：`JobLock` 用 Redisson `tryLock()` 只能保证同一时刻只有一个实例在跑。两个实例的定时器相差几百毫秒触发，而热门文章重算、榜单归档这类任务几十毫秒就跑完，先到的实例释放锁后，后到的实例照样拿得到锁、再跑一遍。`JobLockIT` 的第一个测试复现了这一点（连续调用两次，任务执行了 2 次）。
- **原因**：互斥锁表达的是"不同时"，而需求是"每个周期只一次"。两者只在任务执行时间长于实例间触发时差时才等价。
- **解决**：给 `JobLock.runExclusively` 加一个 `minInterval` 参数：拿到锁后再 `SET NX` 一个带过期时间的 `lock:job:<name>:ran` 标记，设置失败说明这个周期别的实例已经跑过，直接跳过（思路同 ShedLock 的 lockAtLeastFor）。低频任务用它（热门重算 5 分钟，榜单归档、看板归档、对账 1 小时）；计数写回、outbox 中继是高频且幂等的任务，多跑一次无害，仍只用锁。
- **教训**：分布式锁只解决互斥；"只执行一次"还需要按周期去重。同时任务本身也要尽量设计成幂等，作为最后一道保险。
- **相关提交**：本次提交（`JobLock.java`、`ScheduledJobs.java`、`JobLockIT.java`）
