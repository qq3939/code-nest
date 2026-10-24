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
- **相关提交**：2b7b149（`JacksonConfig`）

## P-002 publisher confirm 的回调方法名写错

- **现象**：编译报错，找不到 `confirm.ack()`。
- **原因**：Spring AMQP 的 `CorrelationData.Confirm` 是普通类，方法是 `isAck()`、`getReason()`，不是 record 风格的访问器。
- **解决**：改用 `isAck()` / `getReason()`。另外要同时检查 `correlation.getReturned()`：消息到了交换机但没有队列绑定时，confirm 仍然是 ack，只有 return 回调能发现"不可路由"。
- **教训**：confirm 只表示 Broker 收到了消息，不表示消息进了队列。
- **相关提交**：2b7b149（`OutboxPublisher`）

## P-003 刚写进去的时间读出来对不上

- **现象**：Feed 游标用"毫秒时间戳"定位，偶尔重复返回同一篇文章。
- **原因**：`LocalDateTime.now()` 有微秒甚至纳秒精度，而列是 `DATETIME(3)`。MySQL 存入时对多出来的位数**四舍五入**，于是内存里的值（.1234567）和库里的值（.123）对不上，Redis 里用内存值算的 score 比库里大 1 毫秒。
- **解决**：所有写库的时间统一用 `Times.now()`，先截断到毫秒（`truncatedTo(MILLIS)`）；MyBatis-Plus 的自动填充也用它。
- **教训**：同一个时间会在 Java、MySQL、Redis 三处出现时，要先统一精度。
- **相关提交**：2b7b149（`…/core/util/Times.java:20`）

## P-004 SSE 请求结束后 ThreadLocal 里的用户串到别的请求

- **现象**：（写 SSE 接口时排查出的隐患）SSE 长连接建立后，同一个 Tomcat 线程处理的下一个请求可能拿到上一个用户的身份；另外连接结束时的异步再派发（ASYNC）会因为令牌已过期被拦截器拒绝，日志里出现无意义的 401。
- **原因**：SSE 是异步请求：Controller 返回 `SseEmitter` 后，请求进入异步模式，`HandlerInterceptor.afterCompletion` 不会立刻执行，`UserContext` 的 ThreadLocal 没有被清理，而线程已经回到线程池。
- **解决**：拦截器实现 `AsyncHandlerInterceptor`，在 `afterConcurrentHandlingStarted` 里也清理 ThreadLocal；异步分派（`DispatcherType.ASYNC`）时跳过鉴权，避免重复解析。
- **教训**：用 ThreadLocal 传上下文时，必须确认每一条请求路径（同步、异步、异常）都会清理。
- **相关提交**：2b7b149（`…/web/interceptor/AuthInterceptor.java`）

## P-005 Docker Hub 上的 minio/minio 镜像拉不到新版本

- **现象**：`docker compose pull` 拉 `minio/minio:latest` 得到的是很旧的版本，或者在部分镜像源上直接失败。
- **原因**：MinIO 官方在 2025 年停止在 Docker Hub 发布社区版镜像。
- **解决**：改用 `bitnamilegacy/minio:2025.4.22`（Bitnami 的归档仓库，自带 `mc` 客户端，健康检查可以直接用 `curl`）。数据目录变成 `/bitnami/minio/data`，建桶交给单独的 `minio-init` 容器（见 P-007）。
- **教训**：锁定镜像版本号，不要用 `latest`；记录镜像来源。
- **相关提交**：2b7b149（`docker-compose.yml`）

## P-006 Elasticsearch 的 IK 分词插件下载失败

- **现象**：构建 `deploy/elasticsearch/Dockerfile` 时 `elasticsearch-plugin install` 报 403 或超时。
- **原因**：IK 插件从 `release.infinilabs.com` 下载，这个地址在部分网络环境下不可达；另外 IK 的版本必须和 ES **完全一致**（8.19.5 只能装 8.19.5 的 IK）。
- **解决**：Dockerfile 用 `ARG ES_VERSION` 同时控制两者版本；网络不通时可以先在浏览器里把 zip 下载到 `deploy/elasticsearch/`，改成 `install file:///…` 本地安装。搜索默认关闭（`SEARCH_ENABLED=false`），没有 ES 也能用 MySQL 降级搜索。
- **教训**：外部依赖要有降级方案；在开发容器里无法验证 IK，这一步只能在用户机器上验证。
- **相关提交**：2b7b149（`deploy/elasticsearch/Dockerfile`）

## P-007 MinIO 初始化脚本偶尔建桶失败

- **现象**：`minio-init` 容器有时报 "Server not initialized"，桶没有建出来，上传图片 500。
- **原因**：Bitnami 的 MinIO 镜像首次启动时会自己初始化一遍然后重启服务，健康检查在重启前就已经通过了，`mc mb` 正好撞上重启。
- **解决**：`deploy/minio/init-bucket.sh` 里加重试循环，`mc mb --ignore-existing` 成功并设置匿名下载策略后才退出。
- **教训**：健康检查通过 ≠ 服务完全就绪；初始化脚本要幂等、可重试。
- **相关提交**：2b7b149

## P-008 Windows 上 Elasticsearch 启动后马上退出

- **现象**：ES 容器日志里出现 `max virtual memory areas vm.max_map_count [65530] is too low`。
- **原因**：ES 要求 `vm.max_map_count ≥ 262144`，Docker Desktop 的 WSL2 虚拟机默认是 65530。
- **解决**：在 PowerShell 里执行 `wsl -d docker-desktop sysctl -w vm.max_map_count=262144`（重启后失效）；永久生效需要在 `%USERPROFILE%\.wslconfig` 里配置 `kernelCommandLine = "sysctl.vm.max_map_count=262144"`，然后 `wsl --shutdown`。另外给 Docker Desktop 分配至少 6GB 内存。
- **教训**：README 的 Windows 注意事项里写清楚。
- **补充（2026-10-23 实测）**：在用户本机装的 Docker Desktop 4.96.3 里，`docker run --rm alpine sysctl vm.max_map_count` 默认已经是 262144，不需要手动设置。上面的办法留给旧版本用。
- **相关提交**：30b1b26（`README.md`）

## P-009 Spring Data Redis 的 ZSet 范围查询签名对不上

- **现象**：Feed 多路归并时想在 pipeline 里用 `zRevRangeByScoreWithScores(key, Range, Limit)`，编译不过。
- **原因**：`StringRedisConnection` 上的重载和 `RedisZSetCommands` 上的不一样，`Range<String>` 版本在 String 连接上不可用。
- **解决**：用 `zRevRangeByScoreWithScores(key, min, max, offset, count)` 这个 double 版本；`min` 取 1 而不是 0，顺便把 score 为 0 的占位成员（用来区分"空收件箱"和"收件箱不存在"）排除掉。
- **教训**：pipeline 里只能用 connection 级别的 API，和 `RedisTemplate.opsForZSet()` 不完全一样。
- **相关提交**：2b7b149（`…/service/feed/FeedService.java:126`）

## P-010 列名或别名用了 `reads`，SQL 语法错误

- **现象**：`daily_stat` 建表失败；造数工具在汇总 user_stat 时报 `You have an error in your SQL syntax … near 'reads'`。
- **原因**：`READS` 是 MySQL 的保留字（存储过程的 `READS SQL DATA`）。
- **解决**：建表时列名加反引号 `` `reads` ``；造数 SQL 的别名改成 `read_sum`。
- **教训**：起列名、别名前查一下 MySQL 保留字表，或者干脆避开常见英文单词。
- **相关提交**：2b7b149（V1 迁移脚本）、30b1b26（`DataGenerator`）

## P-011 缓存重建锁的 key 和数据 key 是同一个

- **现象**：两级缓存的集成测试里，`get` 读到一个 UUID 字符串，JSON 解析失败。
- **原因**：重建锁的 key 原来是用 `key.replace("code-nest:cache:", "code-nest:lock:cache:")` 算出来的。测试用的 key 不以 `cache:` 开头，`replace` 没有替换任何内容，锁 key 就等于数据 key，`SET NX` 把锁的令牌（UUID）写进了数据 key。业务代码里的 key 恰好都以 `cache:` 开头，所以平时没暴露。
- **解决**：改成 `lockKeyOf(key)`：去掉前缀后统一加 `lock:`，对任何 key 都不会和原 key 相同。
- **教训**：字符串替换生成 key 时，要考虑"没匹配上"的情况；集成测试最好用和业务不同的 key 形态，能发现隐含假设。
- **相关提交**：44b4994（`…/service/cache/CacheService.java:267`）

## P-012 浏览器不在东八区时"x 小时前"算错

- **现象**：在 UTC 时区的浏览器里，刚发的文章显示"7 小时内"（未来时间）。
- **原因**：后端统一用 `Asia/Shanghai` 的 `LocalDateTime`，序列化成不带时区的字符串 `2026-10-22T11:37:00.123`。dayjs 按浏览器本地时区解析，UTC 浏览器把它当成 UTC 11:37，比真实时间晚 8 小时。
- **解决**：前端用 dayjs 的 utc + timezone 插件，统一 `dayjs.tz(t, 'Asia/Shanghai')` 解析。
- **教训**：接口里的时间最好带时区（`OffsetDateTime` 或时间戳）；既然选了 `LocalDateTime`，就要在前端固定解析时区。
- **相关提交**：a3db0f2（`frontend/src/utils/format.ts`）

## P-013 编辑器的代码高亮、全屏按钮没反应

- **现象**：详情页的代码块没有颜色，浏览器控制台有多条 `ERR_TUNNEL_CONNECTION_FAILED`。
- **原因**：md-editor-v3 默认从 unpkg CDN 动态加载 highlight.js、screenfull、cropperjs，网络不通就加载失败。
- **解决**：本地安装这几个库，用 `config({ editorExtensions })` 注入实例（见 D-024）。
- **教训**：上线前在断网环境下检查一遍控制台的网络请求。
- **相关提交**：a3db0f2（`frontend/src/utils/mdEditor.ts`）

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
- **相关提交**：30b1b26（`application-perf.yml`）

## P-016 集成测试里用 `@MockitoSpyBean` 会让消息被别的上下文消费

- **现象**：（写测试时预判到的问题）原本打算用 spy 让 `NotifyService` 抛异常来测死信，这样消息可能被另一个上下文正常消费掉，测试结果不稳定。
- **原因**：加了 `@MockitoSpyBean` 的测试类会创建一个新的 Spring 上下文，而 Spring 测试框架会缓存之前的上下文，旧上下文里的 `@RabbitListener` 仍然在监听同一个队列（容器是共享的），消息被它抢走。
- **解决**：不用 spy，改为发送一条 payload 本身就无法反序列化的消息（`articleId` 是非数字字符串），消费必然失败，走完重试进入死信。整个 IT 只用一个上下文。
- **教训**：多个测试上下文共享同一个 MQ 时，监听器会互相抢消息；尽量让所有集成测试共用一个上下文配置。
- **相关提交**：44b4994（`MessageReliabilityIT`）

## P-017 压测时缓存场景出现 7%~12% 的 500，日志是 `Unable to connect to Redis`

- **现象**：场景 1（热点文章详情）50 并发跑起来后，部分请求返回 500，后端日志里有 `RedisConnectionFailureException: Unable to connect to Redis` 和 `Connection reset by peer`。低并发时完全正常。
- **原因**：Lettuce 默认所有命令共用一条长连接，但 `executePipelined` 和事务这类命令需要独占连接，没开连接池时每次调用都会新建一条 TCP 连接、用完关掉。高并发下短时间内建连/断连太多，超过了 Redis 和本机网络栈的承受能力。
- **解决**：引入 `commons-pool2`，在 `application.yml` 里打开 `spring.data.redis.lettuce.pool`（max-active 32、min-idle 4、max-wait 2s），独占连接从池里借、用完归还。重跑后错误率归零。
- **教训**：用到 pipeline/事务的项目要开连接池；功能测试发现不了这类问题，必须压测。
- **相关提交**：30b1b26（`code-nest-core/pom.xml`、`application.yml`）

## P-018 定时任务加了分布式锁，仍可能被两个实例先后各执行一次

- **现象**：做双实例演示、核对"定时任务只在一个实例执行"时发现：`JobLock` 用 Redisson `tryLock()` 只能保证同一时刻只有一个实例在跑。两个实例的定时器相差几百毫秒触发，而热门文章重算、榜单归档这类任务几十毫秒就跑完，先到的实例释放锁后，后到的实例照样拿得到锁、再跑一遍。`JobLockIT` 的第一个测试复现了这一点（连续调用两次，任务执行了 2 次）。
- **原因**：互斥锁表达的是"不同时"，而需求是"每个周期只一次"。两者只在任务执行时间长于实例间触发时差时才等价。
- **解决**：给 `JobLock.runExclusively` 加一个 `minInterval` 参数：拿到锁后再 `SET NX` 一个带过期时间的 `lock:job:<name>:ran` 标记，设置失败说明这个周期别的实例已经跑过，直接跳过（思路同 ShedLock 的 lockAtLeastFor）。低频任务用它（热门重算 5 分钟，榜单归档、看板归档、对账 1 小时）；计数写回、outbox 中继是高频且幂等的任务，多跑一次无害，仍只用锁。
- **教训**：分布式锁只解决互斥；"只执行一次"还需要按周期去重。同时任务本身也要尽量设计成幂等，作为最后一道保险。
- **相关提交**：30b1b26（`JobLock.java`、`ScheduledJobs.java`、`JobLockIT.java`）

## P-019 单元测试报 `ClassFormatError: Invalid method Code length 0`

- **现象**：在 Windows 本机第一次跑 `mvnw.cmd -B test`，`RateLimitKeyResolverTest` 的 3 个用例全部报 `ClassFormatError: Invalid method Code length 0 in class file …/AppProperties`；Maven 日志里 compiler 插件显示 "Nothing to compile - all classes are up to date"。
- **原因**：`code-nest-core/target/classes` 里有一份更早生成的类文件（时间是当天 19:20，比源码新，所以 Maven 认为不用重新编译），其中 `AppProperties.class` 是坏的。是哪个工具生成的没有查清（可能是 IDE 的自动构建或更早的一次中断的编译）。
- **解决**：`mvnw.cmd -B clean test`，重新编译后 27 个单元测试全部通过。
- **教训**：Maven 的增量编译只比较时间戳，不校验类文件内容；遇到"源码没问题、类加载报错"的情况，先 `clean` 再判断。CI 每次都是干净环境，所以发现不了这类问题。
- **相关提交**：无代码改动

## P-020 Windows 家庭版装 WSL：`wsl --install --no-distribution` 只回一句"未安装"

- **现象**：在用户本机（Windows 11 家庭版 26200）装 Docker Desktop 前先装 WSL，执行 `wsl --install --no-distribution`，输出"未安装适用于 Linux 的 Windows 子系统。可通过运行 wsl.exe --install 进行安装"，退出码 1。改用 `winget install Microsoft.WSL` 又报 `0x80073d28`（需要管理员权限）。
- **原因**：WSL 还没装时，`C:\Windows\System32\wsl.exe` 只是系统自带的存根，不认 `--no-distribution`。WSL 本体是一个 msix 包，要按整机安装，必须用管理员权限；winget 装这类包不会自动弹 UAC。
- **解决**：在一个管理员 PowerShell 里依次执行 `winget install --id Microsoft.WSL -e`（装上 WSL 2.7.13）、`wsl --install --no-distribution`（这时已经是真正的 wsl.exe，会开启"虚拟机平台"组件）、`winget install --id Docker.DockerDesktop -e`，然后重启。另外 winget 里 Docker Desktop 的安装参数固定带 `--accept-license`，等于替用户接受 Docker 订阅服务协议；这次用 `--override "install --quiet --backend=wsl-2 --no-windows-containers"` 去掉了它，协议由用户第一次打开 Docker Desktop 时自己同意。
- **教训**：Windows 上装系统组件先确认要不要管理员权限，把需要提权的步骤放进同一个管理员终端，只确认一次 UAC；用包管理器装软件时，看一下它会不会替你同意许可协议。
- **相关提交**：无代码改动（README"Windows 注意事项"同步更新）

## P-021 Windows 上 `CountConcurrencyIT` 取数据库连接超时

- **现象**：Windows 本机跑 `mvnw.cmd -B verify`，`CountConcurrencyIT.atomicSqlIsExact` 报 `HikariPool-1 - Connection is not available, request timed out after 3001ms (total=20, active=20, idle=0, waiting=33)`；单独重跑时 `naiveLosesUpdates` 也报同样的错（waiting=216）。CI（Linux）上一直通过。
- **原因**：测试让 200 个线程各加 5 次，更新的是同一行。atomic-sql 的 `UPDATE … SET c = c + 1` 要拿行锁，1000 次更新只能一条接一条地执行；而 MySQL 8.4 默认每次提交要刷两次盘（`innodb_flush_log_at_trx_commit=1`、`sync_binlog=1`）。在 Docker Desktop 的 WSL2 虚拟磁盘上实测单条自动提交的 UPDATE 约 7.9ms（关掉 binlog 约 3.6ms），1000 次约 8 秒。连接池只有 20 个连接，取连接超时是 3 秒（`application.yml` 里为了快速失败设的），排在后面的线程就超时了。
- **解决**：只在测试 profile（`application-test.yml`）里把 `spring.datasource.hikari.connection-timeout` 放宽到 30 秒；默认配置的 3 秒不变。修改后三个用例的耗时：atomic-sql 11.0 秒、naive 4.4 秒（丢了 986 次更新）、redis-batch 1.5 秒。
- **教训**：并发测试的结果不只取决于代码，还取决于磁盘、网络这些环境因素；断言正确性的测试，不要和"多快"的超时绑在一起。这个数字也说明了 redis-batch 的价值：同一行的热点更新，瓶颈在数据库每次提交的刷盘上。另外，在本机重跑压测（A1-2）时，atomic-sql 场景会明显受这一点影响，写报告时要说明。
- **相关提交**：e4ca1e2（`application-test.yml`）

## P-022 ES 索引一直写不进去：`publishTime` 只写出了日期

- **现象**：第一次在本机开 ES（A0-6），发布了 6 篇文章，`article` 索引里却是 0 篇。后端日志里 `search.queue` 每条消息都报 `failed to parse field [publishTime] of type [date] … Preview of field's value: '2026-10-23'`，重试 3 次后全部进了死信。也就是说，ES 搜索这条链路从写完到现在一次都没真正工作过。
- **原因**：`ArticleDoc.publishTime` 上写的是 `@Field(type = Date, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")`。Spring Data Elasticsearch 的 `format` 有默认值（`date_optional_time`、`epoch_millis`），`pattern` 只是追加在后面，**写入时用的是第一个 format**。`date_optional_time` 的时间部分在可选段里，而且带时区偏移；`LocalDateTime` 没有时区，整段可选内容被跳过，只剩 `2026-10-23`。索引的 mapping 是手写的，只接受带时间的格式，于是整篇文档被拒。
- **解决**：改成 `@Field(type = Date, format = {}, pattern = "…")`，`format = {}` 让 `pattern` 成为唯一格式。新增单元测试 `ArticleDocMappingTest`，不启动 ES，直接用 Spring Data 的转换器检查写出来的字符串，修复前能稳定复现。已经进死信的 6 条消息用管理端"死信重放"重新投递，1.1 秒内全部写入索引。
- **教训**：只在"开关打开"时才走的代码路径，没有测试就等于没验证过；README 里"ES 那一侧没有任何测试"这句话，这次正好应验。死信机制兜住了这次故障：消息没丢，修复后重放即可恢复。
- **相关提交**：a13f794（`ArticleDoc.java`、`ArticleDocMappingTest.java`）

## P-023 后端容器重建后，前端访问 `/api` 一直 502

- **现象**：把 `.env` 里的 `SEARCH_ENABLED` 改成 `true` 后执行 `docker compose --profile search up -d`，后端容器被重建，健康检查是 healthy，但浏览器经 8080 访问 `/api` 全部 502，直到 `docker compose restart frontend` 才恢复。
- **原因**：`upstream` 里的 `server backend:8081;` 只在 Nginx 启动时解析一次服务名，之后一直用当时拿到的 IP。后端容器一旦被重建（改了 `.env`、重新构建镜像）就换了 IP，Nginx 还在连旧地址。双实例演示要先 `restart frontend` 也是同一个原因。
- **解决**：Nginx 1.27.3 起，开源版的 `upstream` 也支持 `resolve` 参数：加上 `resolver 127.0.0.11 valid=10s`（Docker 内置 DNS）和 `zone`，`server backend:8081 resolve;` 每 10 秒重新解析一次。验证过三种情况都不用重启 frontend：后端重建后立即可用；`--scale backend=2` 后约 10 秒开始轮询两个实例（40 次请求分成 19 和 20）；缩回 1 个实例后 20 次请求全部 200。
- **顺带**：后端的端口映射是 `8081-8082:8081`（给双实例演示用），单实例时重建也可能被分到 8082，因为旧容器的 8081 还没释放。这是端口范围的正常行为，不改；用 `docker compose port backend 8081` 查当前端口，或者直接经 8080 访问。
- **教训**：反向代理里写服务名时，要确认它多久解析一次；容器的 IP 不是固定的。
- **相关提交**：294e3c5（`deploy/nginx/nginx.conf`）

## P-024 ES 搜索的高亮片段没有转义 HTML

- **现象**：搜索标题为 `<img src=x onerror=alert(1)> 高亮转义测试` 的文章，ES 返回的高亮片段把 `<img …>` 原样带了回来。前端有 DOMPurify 兜底（只放行 `<em>`），脚本不会执行，但尖括号里的内容会被当成标签删掉：标题是"Java 泛型 List<String> 详解"的文章，在搜索结果里会显示成"Java 泛型 List 详解"。
- **原因**：ES 的高亮默认 `encoder: default`，只在命中的词两边加标签，原文不做转义。而 MySQL 降级路径的 `SearchService.highlight()` 是先转义再插 `<em>`，前端注释也写着"后端已经转义了正文"，两条路径的约定不一致。
- **解决**：高亮参数加上 `withEncoder("html")`，ES 会先转义原文再插入 `<em>`。修复后接口返回 `&lt;img …&gt; <em>高亮</em>…`，浏览器里标题按原文显示。
- **教训**：同一个接口有两种实现时，输出约定（这里是"只信任 `<em>`"）要两边都检查；前端兜底只能防住执行脚本，防不住内容被删。
- **相关提交**：711550c（`SearchService.java`）

## P-025 Git Bash 里用 curl 发中文，服务端收到乱码

- **现象**：在 Git Bash 里执行 `curl localhost:9200/article/_analyze -d '{"analyzer":"ik_smart","text":"Redis缓存击穿"}'`，ES 报 `Invalid UTF-8 start byte 0xbb`。
- **原因**：Windows 版 curl 按系统代码页（中文系统是 GBK）读取命令行参数，中文被转成了 GBK 字节再发出去；URL 里的中文查询参数也一样。
- **解决**：把请求体写进 UTF-8 文件，用 `--data-binary @body.json` 发送；URL 里的中文先做 URL 编码；或者改用 Python 发请求。
- **教训**：Windows 上命令行参数的编码不一定是 UTF-8，带中文的测试请求尽量从文件读。
- **相关提交**：无代码改动

## P-026 SSE 连接表"先查后删"，同一用户的新连接可能被一起删掉

- **现象**：（为 A0-8 补并发知识点、读 `SseHub` 时发现的隐患）用户的最后一个 SSE 连接断开、同时又建立新连接（比如刷新页面）时，新连接有可能从连接表里消失：连接本身还开着，但之后收不到任何未读推送，心跳也不再发。
- **原因**：`remove` 原来分三步：`connections.get(userId)` 拿到集合 → 删掉这个连接 → 集合空了就 `connections.remove(userId, 集合)`；`connect` 是 `computeIfAbsent(userId, …).add(新连接)`。如果 `connect` 正好在"判空"和"删 key"之间拿到同一个集合并把新连接加进去，随后整个集合连同新连接被删掉。`ConcurrentHashMap` 只保证单个方法是原子的，"先查再改"的组合不是。
- **解决**：`connect` 改用 `compute`，在函数里建集合并加入连接；`remove` 改用 `computeIfPresent`，删掉连接后集合为空就返回 `null`（`ConcurrentHashMap` 会删除这个 key）。两者对同一个 key 在同一把桶锁里执行，不会再交错。发送消息仍在 `compute` 外面，因为 `compute` 的函数里不能修改同一个 Map，也不该做耗时操作。
- **教训**：用并发容器不等于线程安全，要看每一组"先查后改"是不是在同一个原子操作里。这类竞态的时间窗口只有几条指令，很难写出稳定复现的测试，这次是靠读代码发现的，没有补测试。
- **相关提交**：573bf4f（`SseHub.java`）
