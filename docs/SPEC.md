# 项目 A 执行文档（SPEC）

> 项目名暂用占位名 `project-a`。正式名称在 M0 开工时向用户确认，下文用 `xxx` 指代。
> 本文的读者是在本文件夹里开工的 Claude Code 会话，下称"执行者"。

## 0. 执行者须知

### 0.1 用户情况

用户是大二学生，目标是 2027 年春季的日常实习，方向是 Java 后端和 AI 应用开发。除了算法题，用户不自己写代码，主要靠读代码、跑实验、看文档来学习。因此：

- 代码要清晰，分层明确。注释只写"为什么这样做"，不写"这行在做什么"。
- 每个里程碑结束时写一份学习笔记 `docs/learn/M<n>.md`，格式见第 16 节。
- 关键选型记进 `docs/decisions.md`，踩到的坑记进 `docs/pitfalls.md`。

### 0.2 工作方式

1. 先通读本文件，再按第 15 节的里程碑顺序推进，不跳步。
2. 每个里程碑都必须达到它的"完成标准"，然后依次：
   - 跑全部测试；
   - 用 `docker compose up` 验证；
   - 更新 README 和三份学习文档；
   - 提交一次；
   - 向用户简短汇报：做了什么、怎么验证的、还有什么没做；
   - 接着做下一个里程碑。
3. 缺少只有用户能提供的信息（见第 1 节）时，停下来问。其他细节按本文件和常规做法自己定，并记进 `docs/decisions.md`。
4. 不扩大范围。第 3.2 节列为"不做"的功能不要做；要加新的重要依赖或改动功能边界，先问用户。
5. 本项目参考开源项目技术派（https://github.com/itwanger/paicoding ，`springboot3&vue3` 分支）的业务设计，但代码从空项目写起，**不复制技术派的代码**。README 里要注明参考来源。

## 1. 开工前向用户确认

- **项目正式名称**：英文小写，用连字符分隔，例如 `dev-forum`。用作仓库名和 Maven `artifactId` 的前缀。
- **GitHub 用户名**：包名用 `io.github.<用户名>.<去掉连字符的项目名>`。
- **本机环境**：
  - JDK 17（用户已装 Amazon Corretto 17）、Git、Node.js LTS；
  - Docker Desktop（WSL2 后端），分配给它的内存至少 6 GB，Elasticsearch 需要。
  - 缺什么就告诉用户怎么装，不要替用户改系统设置。
- 本项目**不需要**任何付费 API Key。

## 2. 定位

面向开发者的技术社区：写文章，点赞、收藏、评论，关注作者看动态，收通知，看排行，全文搜索。

后端的设计重点是三类问题：
- **读多写多**：文章详情和各种计数。
- **热点集中**：热门文章和大 V。
- **消息必须可靠**：通知、动态分发、搜索同步。

关键方案都实现成**可切换的策略**：同一套代码改一下配置就能做对照压测，"改进前后"的数据全部出自本项目。

## 3. 功能边界

### 3.1 要做的

**用户端**

| 模块 | 功能 |
|---|---|
| 账号 | 用户名 + 密码注册登录（BCrypt）；访问令牌 + 刷新令牌；退出；连续登录失败锁定；修改资料和密码；上传头像 |
| 文章 | Markdown 编辑和实时预览；草稿每 30 秒自动保存；发布、修改、删除；分类（单选）和标签（多选，可新建）；封面图；详情页（目录、阅读数）；最新列表、热门列表，可按分类和标签筛选 |
| 互动 | 文章点赞和收藏；两级评论（评论 + 回复，回复显示被回复人）；评论点赞；删除自己的评论 |
| 关注 | 关注和取关；关注列表、粉丝列表；首页"关注"标签页显示关注动态（Feed），下拉加载更多 |
| 通知 | 评论、回复、点赞、收藏、关注、系统六类；按类型分页查看；未读数通过 SSE 实时推送到页头；单条或全部标记已读 |
| 排行 | 作者活跃榜（日、周、月）；热门文章榜（热度分随时间衰减） |
| 搜索 | 标题、摘要、正文全文搜索，关键词高亮；Elasticsearch 关闭时退回 MySQL 模糊查询 |
| 个人主页 | 资料和统计（文章数、粉丝数、获赞数、阅读数）；标签页：文章、收藏、关注、粉丝 |

**管理端**（同一个前端应用，`/admin` 路由，只有管理员能进）

| 模块 | 功能 |
|---|---|
| 数据看板 | 近 30 天的日活（HyperLogLog 统计）、新增用户、新增文章、阅读量、点赞量趋势 |
| 内容审核 | 命中"审核级"敏感词的文章进入待审核列表，可以通过或驳回；已发布文章可以下架和恢复 |
| 用户管理 | 搜索用户；封禁和解封，封禁后该用户的刷新令牌立即作废 |
| 分类和标签 | 分类的增删改和排序；标签的合并和删除 |
| 敏感词 | 增删改；分"拦截、审核、替换"三级；修改后所有实例实时生效 |
| 死信消息 | 查看进入死信的消息和失败原因，可一键重新投递 |
| 操作日志 | 管理员的审核、封禁、下架等操作记录 |
| 实验开关 | 仅 dev 环境：运行时切换计数、Feed、缓存策略（见 9.14） |

### 3.2 不做的

- 手机号、邮箱验证码登录（需要外部服务），以及微信等第三方登录；
- 私信和聊天；
- 支付、积分商城；
- MongoDB；
- 微服务拆分、服务注册与发现；
- 移动端 App；
- 富文本编辑（只用 Markdown）；
- 文章版本历史。

## 4. 技术栈

版本原则：开工时取当时最新的稳定小版本，锁定在 `pom.xml` 和 `package.json` 里，之后不随意升级。

| 类别 | 选型 | 说明 |
|---|---|---|
| 语言与框架 | Java 17、Spring Boot 3.5.x | 用户本机装的是 Corretto 17；3.x 的学习资料最多 |
| 数据访问 | MyBatis-Plus 3.5.x（`mybatis-plus-spring-boot3-starter`）、MySQL 8.4 LTS、HikariCP | 复杂查询写在 XML 里 |
| 数据库迁移 | Flyway | 建表、改表都写迁移脚本 `V<n>__<说明>.sql` |
| 缓存 | Redis 7.x、Caffeine、Redisson | Redisson 只用来做分布式锁 |
| 消息 | RabbitMQ 4.x（management 镜像）、Spring AMQP | |
| 搜索 | Elasticsearch 8.x + IK 分词插件、Spring Data Elasticsearch | IK 插件的版本必须和 ES 完全一致 |
| 对象存储 | MinIO + 官方 Java SDK | 头像、封面、文章里的图片 |
| 认证 | jjwt 0.12.x、`spring-security-crypto`（只用其中的 BCrypt） | 不引入完整的 Spring Security，用拦截器做鉴权 |
| 接口文档 | springdoc-openapi | 访问 `/swagger-ui.html` |
| 工具 | Lombok、Jackson | 不引入 MapStruct、Hutool |
| 测试 | JUnit 5、Mockito、AssertJ、Testcontainers（MySQL、Redis、RabbitMQ） | |
| 压测 | JMeter 5.6 | 用 Docker 镜像运行，本机不用装 |
| 监控（可选） | Actuator、Micrometer、Prometheus、Grafana | 放在 Compose 的 `monitoring` profile 里 |
| 前端框架 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios | |
| 前端组件 | md-editor-v3（编辑和预览）、DOMPurify（防 XSS）、ECharts（看板）、dayjs、`@microsoft/fetch-event-source`（能带请求头的 SSE） | |
| 部署 | Docker Compose、Nginx | Nginx 负责静态资源，并反向代理 `/api`、`/files` |
| CI | GitHub Actions | 后端跑 `./mvnw -B verify`，前端跑 `npm ci && npm run build` |

## 5. 架构

### 5.1 组件

```
浏览器 ──> Nginx :8080 ──┬── /        前端静态资源（Vue 构建产物）
                         ├── /api/    ──> backend :8081（可扩成多个实例）
                         └── /files/  ──> MinIO :9000
backend ──> MySQL          业务数据、本地消息表、死信记录
        ──> Redis          缓存、计数、Feed、排行、限流、令牌、Pub/Sub
        ──> RabbitMQ       领域事件：通知、Feed 扇出、搜索同步、排行积分、缓存补偿
        ──> Elasticsearch  文章搜索（可关）
        ──> MinIO          图片
```

### 5.2 工程结构（`xxx` 替换为项目名）

```
project-a/
├── backend/
│   ├── pom.xml              # 父 POM，统一管理依赖版本
│   ├── xxx-api/             # DTO、VO、枚举、错误码、事件消息体
│   ├── xxx-core/            # 通用组件：Result、异常、工具类；Redis/MQ/ES/MinIO 配置；
│   │                        # 限流注解和切面、分布式锁、JWT、前缀树
│   ├── xxx-service/         # 业务，按功能分包：user、article、interaction、count、follow、
│   │                        # feed、notify、rank、search、admin、stat、outbox
│   └── xxx-web/             # Controller、拦截器、全局异常处理、SSE、定时任务入口、
│                            # application*.yml、Flyway 脚本、启动类、造数入口
├── frontend/                # Vue 3 应用，用户端和管理端按路由区分
├── deploy/                  # nginx.conf、装好 IK 的 ES Dockerfile、MinIO 初始化脚本、监控配置
├── perf/                    # JMeter 计划、运行脚本、结果
├── docs/                    # SPEC.md、decisions.md、pitfalls.md、learn/、perf-report.md
├── docker-compose.yml
├── .env.example
└── README.md
```

模块的划分和技术派一致（api、core、service、web），方便用户对照学习。

### 5.3 分层与编码约定

- **分层职责**：Controller 只做参数校验、调用 Service；Service 写业务逻辑；Mapper 只管数据访问。
- **对象分工**：
  - DO（数据库实体）、DTO（服务之间传递）、VO（返回给前端）三者分开。
  - 转换写成静态方法，例如 `ArticleVO.from(...)`。
- **统一返回**：所有接口返回 `Result<T>{code, message, data, traceId}`。错误码按模块分段：1xxxx 通用，2xxxx 用户，3xxxx 文章，4xxxx 互动，5xxxx 关注和 Feed，6xxxx 通知，9xxxx 管理。
- **traceId**：过滤器为每个请求生成 traceId，放进 MDC 打到日志里，并通过响应头 `X-Trace-Id` 返回。
- **ID**：用 MyBatis-Plus 的 `ASSIGN_ID`（雪花算法）。Jackson 要把 `Long` 序列化成字符串，否则前端 JS 会丢精度。这个坑写进 pitfalls。
- **时间**：Java 里用 `LocalDateTime`，数据库用 `DATETIME(3)`，时区统一为 Asia/Shanghai。
- **删除方式**：
  - 文章和评论用逻辑删除；
  - 点赞、收藏、关注这类带唯一约束的关系表用状态字段，不用逻辑删除，因为逻辑删除会和唯一索引冲突。

## 6. 数据模型

所有表都有：`id BIGINT` 主键（雪花 ID）、`create_time`、`update_time`；引擎 InnoDB，字符集 utf8mb4。

| 表 | 关键字段 | 索引与约束 | 说明 |
|---|---|---|---|
| user | username、password_hash、nickname、avatar_url、bio、role（USER/ADMIN）、status（NORMAL/BANNED） | UNIQUE(username) | |
| article | author_id、title、summary、cover_url、category_id、status（DRAFT/PENDING/PUBLISHED/OFFLINE）、publish_time、deleted | (status, publish_time)、(author_id, status, publish_time) | 正文单独放在 article_content |
| article_content | article_id（主键）、content MEDIUMTEXT | | 大字段拆表，列表查询不带正文 |
| category | name、sort | UNIQUE(name) | |
| tag | name | UNIQUE(name) | |
| article_tag | article_id、tag_id | 主键 (article_id, tag_id)；索引 (tag_id) | |
| comment | article_id、user_id、parent_id（0 表示一级评论）、reply_to_user_id、content、like_count、deleted | (article_id, parent_id, create_time) | |
| like_record | user_id、target_type（ARTICLE/COMMENT）、target_id、status（1/0） | UNIQUE(user_id, target_type, target_id)、(target_type, target_id, status) | 取消点赞只改状态，不删行 |
| collect_record | user_id、article_id、status | UNIQUE(user_id, article_id) | |
| follow | follower_id、followee_id、status | UNIQUE(follower_id, followee_id)、(followee_id, status) | |
| article_stat | article_id（主键）、read_count、like_count、collect_count、comment_count | | 计数策略作用在这张表上 |
| user_stat | user_id（主键）、article_count、follower_count、following_count、like_received、read_received | | |
| notify_message | receiver_id、sender_id、type、target_type、target_id、content、is_read、msg_id | UNIQUE(msg_id)、(receiver_id, is_read, create_time) | msg_id 用于消费幂等 |
| outbox_message | msg_id（唯一）、exchange、routing_key、payload（JSON）、status（NEW/SENT/FAILED）、retry_count、next_retry_time | (status, next_retry_time) | 本地消息表 |
| dead_letter_message | msg_id、queue、payload、error、failed_time、status（NEW/REPLAYED/IGNORED） | (status, failed_time) | 死信落库，供管理端查看和重放 |
| sensitive_word | word、level（BLOCK/REVIEW/REPLACE）、enabled | UNIQUE(word) | |
| rank_snapshot | rank_type、period、rank_no、subject_id、score | (rank_type, period, rank_no) | 榜单归档 |
| daily_stat | stat_date（唯一）、dau、new_users、new_articles、reads、likes | | 看板数据 |
| admin_audit_log | admin_id、action、target_type、target_id、reason | (admin_id, create_time) | |

## 7. Redis 设计

所有 key 都加 `<项目名>:` 前缀，下表省略。

| Key | 类型 | 用途 | 过期 |
|---|---|---|---|
| `auth:refresh:{userId}:{tokenId}` | String | 刷新令牌 | 7 天 |
| `auth:login-fail:{username}` | String（计数） | 登录失败次数 | 15 分钟 |
| `user:status:{userId}` | String | 用户状态缓存，让封禁立即生效 | 10 分钟 |
| `count:article:{id}` | Hash（read/like/collect/comment） | redis-batch 策略的计数 | 不过期，靠对账兜底 |
| `count:dirty:article` | Set | 待写回数据库的文章 ID | — |
| `like:article:{id}` | ZSet（userId → 时间） | 判断是否点过赞、显示最近点赞的人 | 7 天，缓存未命中时从库重建 |
| `feed:inbox:{userId}` | ZSet（articleId → 发布时间） | 收件箱，最多 500 条 | 30 天没读就过期 |
| `feed:outbox:{authorId}` | ZSet | 发件箱（pull、hybrid 模式用），最多 1000 条 | — |
| `feed:bigv-follow:{userId}` | Set | 该用户关注的大 V | 10 分钟 |
| `rank:active:day:{yyyyMMdd}` | ZSet | 作者活跃日榜 | 35 天 |
| `rank:active:week:{yyyyMMdd}`、`rank:active:month:{yyyyMMdd}` | ZSet | 由日榜 ZUNIONSTORE 合成 | 5 分钟 |
| `rank:hot:article` | ZSet | 热门文章前 200 | — |
| `rank:dedup:{msgId}` | String | 排行积分的消费去重 | 1 天 |
| `cache:article:{id}` | String（JSON，不含计数） | 文章详情缓存 | 30 分钟 + 随机 0–5 分钟 |
| `cache:hot-list` | String（值里带逻辑过期时间） | 首页热门列表 | 不设 TTL，用逻辑过期 |
| `lock:cache:article:{id}` | String | 重建缓存的互斥锁 | 3 秒 |
| `lock:job:{jobName}` | Redisson 锁 | 防止定时任务在多个实例上重复执行 | 看门狗自动续期 |
| `ratelimit:{name}:{dim}:{id}` | ZSet | 滑动窗口限流 | 窗口长度 |
| `submit:token:{token}` | String | 防重复提交令牌 | 10 分钟 |
| `uv:day:{yyyyMMdd}` | HyperLogLog | 日活 | 35 天 |
| `notify:unread:{userId}` | String（计数） | 未读数 | 不过期，变化时更新 |

Pub/Sub 频道：
- `cache:invalidate`：两级缓存失效广播；
- `notify:push`：跨实例 SSE 推送；
- `sensitive:refresh`：敏感词变更广播；
- `strategy:refresh`：策略切换广播。

## 8. RabbitMQ 设计

**交换机**：`xxx.event`（topic，持久化）、`xxx.retry`（direct）、`xxx.dlx`（direct）。

**消息体**：统一为 `{msgId, eventType, occurredAt, payload}`，JSON 格式，持久化投递。

| 队列 | 绑定的路由键 | 消费者做什么 | 幂等方式 |
|---|---|---|---|
| `notify.queue` | `interaction.liked`、`interaction.collected`、`comment.created`、`comment.replied`、`user.followed`、`system.notice` | 写 notify_message，更新未读数，发布到 `notify:push` | notify_message.msg_id 唯一索引 |
| `feed.queue` | `article.published`、`article.removed`、`user.followed` | 扇出到粉丝收件箱 / 从收件箱移除 / 关注时回填 | ZADD 本身幂等 |
| `search.queue` | `article.published`、`article.updated`、`article.removed` | 写入或删除 ES 文档 | 按文章 ID upsert |
| `rank.queue` | `article.published`、`comment.created`、`interaction.liked`、`interaction.collected` | 给作者加活跃分 | `rank:dedup:{msgId}` 用 SET NX 去重 |
| `cache.retry.queue` | `cache.delete` | 重试删除缓存 | 删除操作本身幂等 |

**消费端**：手动确认（`AcknowledgeMode.MANUAL`），处理成功才 ack；预取数量 20；并发消费者 2–8 个。

**重试和死信**：
- 处理失败时，把消息投到该队列的重试队列 `<队列>.retry.<n>`，n 为 1、2、3，消息 TTL 分别是 1 秒、5 秒、30 秒；重试队列的死信交换机指回原队列，过期后消息自动回到原队列。
- 第 3 次重试仍失败，就投到死信队列 `<队列>.dlq`。
- 死信消费者把消息写进 dead_letter_message 表，然后 ack。

## 9. 关键设计

### 9.1 认证与账号

- 登录成功后返回两个令牌：
  - 访问令牌：JWT，HS256 签名，有效期 30 分钟，带 userId、role、jti；
  - 刷新令牌：随机 ID，存 Redis，有效期 7 天。
- 每次刷新都轮换刷新令牌，旧的立即作废；退出时删除刷新令牌。
- 拦截器校验访问令牌，并查 `user:status` 缓存，所以封禁能立即生效；管理端接口要求 role=ADMIN。
- 连续登录失败 5 次锁定 15 分钟。
- 第一个管理员账号在启动时根据 `.env` 里的 ADMIN_USERNAME、ADMIN_PASSWORD 创建，不存在才创建。
- 前端：令牌存在 Pinia + localStorage。Axios 拦截器遇到 401 时，自动用刷新令牌换新令牌；多个请求同时 401 时只刷新一次。

### 9.2 计数（配置项 `app.count.strategy`）

定义 `CountService` 接口，有三种实现：
- **naive**：读出 article_stat 的当前值，加 1 后整行写回。故意复现并发覆盖问题，作为对照组，代码注释里要写明。
- **atomic-sql**：`UPDATE article_stat SET read_count = read_count + 1 WHERE article_id = ?`。
- **redis-batch**（默认）：
  - 计数时执行 `HINCRBY`，同时把文章 ID 加进 `count:dirty:article`；
  - 定时任务每 10 秒（可配置）取一批脏 ID，把 Redis 里的绝对值批量写回数据库（`INSERT ... ON DUPLICATE KEY UPDATE`）；
  - 每天和数据库对账一次。

读计数时，redis-batch 从 Redis 读，未命中再从库加载；另外两种直接读库。

写进 decisions 的讨论点：
- Redis 宕机时最多丢多少数据，AOF `everysec` 的影响；
- 为什么写回绝对值而不是增量；
- 三种策略各自的代价。

### 9.3 关注动态 Feed（配置项 `app.feed.mode`、`app.feed.big-v-threshold`，阈值默认 5000）

三种模式：
- **push**：发文后发出 `article.published` 消息，由 `feed.queue` 消费：分批读粉丝（每批 500 人），用 pipeline 把文章 ZADD 进各粉丝的收件箱，再把收件箱修剪到 500 条。
- **pull**：每个作者维护自己的发件箱；读的时候把所关注作者的发件箱合并起来。
- **hybrid**（默认）：粉丝数达到阈值的作者只写自己的发件箱；读的时候合并"自己的收件箱 + 所关注大 V 的发件箱"。

分页用游标：
- 游标 =（上一页最小的时间戳，该时间戳已经返回了几条）；
- 单一来源时用 `ZREVRANGEBYSCORE key max -inf LIMIT offset size` 取下一页；
- 多个来源时，每个来源各取 size 条，按时间归并后再算出新游标。
- 必须有测试保证翻页时不重复、不遗漏。

其他处理：
- **读时过滤**：已删除、已下架的文章，以及已取关作者的文章，都在读的时候批量查状态过滤掉（状态查询带缓存）。
- **关注回填**：关注一个人时，把对方最近 20 篇文章回填进自己的收件箱。
- **冷启动**：收件箱不存在时，从数据库重建最近 500 条。

### 9.4 缓存（配置项 `app.cache.mode`）

**缓存对象**：
- 文章详情：缓存里不含计数，计数单独读。这样计数变化不会让缓存频繁失效。
- 首页热门列表。

**三种模式**：`none`、`redis`、`two-level`（默认）。two-level 是 Caffeine（最多 1 万条，写入后 60 秒过期）+ Redis。

**读路径**：本地缓存 → Redis → 数据库。回填 Redis 时在 TTL 上加随机值，防止缓存雪崩。

**三大问题**：
- **防穿透**：对不存在的 ID 缓存空值，保留 2 分钟。
- **防击穿**：
  - 文章详情：重建时加互斥锁，拿不到锁的请求等一小会儿再读缓存；
  - 热门列表：用逻辑过期，过期后由一个线程异步重建，其他请求先返回旧值。

**写路径**：
- 在事务提交后（`TransactionSynchronization.afterCommit`）删除 Redis 里的缓存；
- 再发布 `cache:invalidate`，让各实例清掉自己的本地缓存；
- 如果删 Redis 失败，就发一条 `cache.delete` 消息进重试队列补偿。

### 9.5 消息可靠性：本地消息表 + 确认 + 重试 + 死信 + 幂等

- **发送端**：
  - 业务数据和 outbox_message 在同一个事务里写入；
  - 事务提交后立即异步投递，开启 publisher confirm（correlated 模式）和 returns，收到确认后把 outbox 记录标为 SENT；
  - 中继任务每 5 秒捞一次状态为 NEW、且已到重试时间的记录重新投递，按指数退避；10 次仍失败就标为 FAILED 并告警。
- **Broker**：交换机、队列都持久化，消息持久化投递。
- **消费端**：手动确认 + 重试队列 + 死信，见第 8 节；幂等方式见第 8 节的表格。

写进 decisions 的讨论点：
- 为什么需要本地消息表：防止"写库成功，但消息没发出去"；
- 和 RocketMQ 事务消息比较各有什么取舍；
- 消息顺序没有保证时怎么处理，比如"取关"比"关注"先到。

### 9.6 限流防刷

- 写法：在接口上加 `@RateLimit(name, window, limit, dims = {USER, IP})`，由 AOP 切面调用 Lua 脚本。
- 算法：ZSet 滑动窗口，Lua 里原子地完成四步：清掉窗口外的记录 → 统计 → 未超限就写入 → 设置过期。
- 默认规则（都能在配置里改）：

| 接口 | 维度 | 限制 |
|---|---|---|
| 登录、注册 | IP | 每分钟 10 次 |
| 点赞、收藏 | 用户 | 每秒 5 次 |
| 评论 | 用户 | 每分钟 10 次 |
| 发文 | 用户 | 每分钟 3 次 |
| 搜索 | IP | 每秒 10 次 |

- 超限时返回 HTTP 429 和统一错误码。

### 9.7 敏感词

- 自己实现前缀树（DFA）做匹配，不用第三方库。
- 启动时从库里加载词库；管理端修改后广播，各实例重建前缀树。
- 发文时：命中"拦截级"直接拒绝，命中"审核级"进入待审核，命中"替换级"替换成 `*`。评论只做替换。

### 9.8 搜索

- **索引**：`article` 索引，title、summary、content 三个字段建索引时用 `ik_max_word`，搜索时用 `ik_smart`；另外存 tags、authorName、publishTime、status。
- **同步**：
  - 日常由 `search.queue` 消费消息同步；
  - 管理端提供"全量重建索引"（批量写入），造完数据后用它建索引。
- **查询**：multi_match + 高亮。`app.search.enabled=false` 时改走 MySQL LIKE，只搜标题和摘要。
- **Windows 上的坑**：Docker Desktop 下 ES 要求 `vm.max_map_count ≥ 262144`，写进 README 和 pitfalls。

### 9.9 排行

- **活跃分**：发文 +10，发评论 +2，文章被点赞 +2，被收藏 +3，数值可配置。由 `rank.queue` 消费，累加到日榜。
- **周榜、月榜**：对最近 7 天、30 天的日榜做 ZUNIONSTORE，结果缓存 5 分钟。
- **热门文章**：每 10 分钟，对最近 7 天的文章计算热度分，取前 200 写进 ZSet。公式：

  热度分 = (点赞 × 2 + 收藏 × 3 + 评论 × 4 + 阅读 × 0.1) / (发布至今的小时数 + 2)^1.5

- **归档**：每天 00:05 把昨天榜单的前 100 名写进 rank_snapshot。

### 9.10 定时任务

| 任务 | 频率 |
|---|---|
| 计数写回 | 每 10 秒 |
| outbox 中继 | 每 5 秒 |
| 热度分计算 | 每 10 分钟 |
| 榜单归档 | 每天 00:05 |
| 日统计 | 每天 00:10 |
| 计数对账 | 每天 03:00 |

每个任务都用 Redisson 锁 `lock:job:{name}`，防止多个实例重复执行；用 tryLock，拿不到锁就跳过，不等待。

### 9.11 防重复提交

- 打开编辑器时向后端领取一个提交令牌，存在 Redis，有效 10 分钟。
- 发布时在请求头 `Idempotency-Token` 里带上令牌。服务端原子地"检查并删除"令牌（用 Lua 脚本或 DEL 的返回值判断），删除成功才执行发布。

### 9.12 文件上传

- 只接受 jpg、png、webp、gif，大小不超过 5 MB；按文件头判断真实类型，不信扩展名。
- 对象名格式为 `yyyy/MM/<uuid>.<ext>`。
- 通过 Nginx 的 `/files/` 访问，不直接暴露 MinIO。

### 9.13 SSE 推送未读数

- 接口是 `GET /api/notifications/stream`。前端用 fetch-event-source 携带 Authorization 请求头，因为原生 EventSource 只能发 GET，也不能带自定义请求头。
- 每个实例在内存里保存连到本机的连接。有新通知时，通过 Redis `notify:push` 广播，各实例把消息推给自己持有的连接。
- 每 25 秒发一次心跳；连接断开时自动清理。

### 9.14 策略切换和实验开关

- 启动时的默认值来自配置：`app.count.strategy`、`app.feed.mode`、`app.cache.mode`。
- dev 环境提供管理端接口和页面，能在运行时切换策略；变更通过 `strategy:refresh` 广播到所有实例。prod 环境关闭这个接口。

### 9.15 安全

- SQL 一律用 `#{}` 传参，不用 `${}` 拼接。
- Markdown 渲染前用 DOMPurify 清洗，防 XSS。
- 令牌放在请求头里，不用 Cookie 鉴权，所以不需要 CSRF 令牌。
- 管理端接口统一鉴权。
- 密钥只从环境变量读取。
- 上传文件校验类型和大小。
- 错误信息不向前端暴露堆栈。

## 10. 接口清单

统一前缀 `/api`。列表接口中，Feed、评论、通知用游标分页，其余用页码分页。

| 模块 | 方法与路径 | 说明 |
|---|---|---|
| 认证 | POST /auth/register、POST /auth/login、POST /auth/refresh、POST /auth/logout | |
| 用户 | GET /users/me、PUT /users/me、PUT /users/me/password | 当前用户 |
| 用户 | GET /users/{id}、GET /users/{id}/articles、GET /users/{id}/collections、GET /users/{id}/followers、GET /users/{id}/followings | 个人主页 |
| 文件 | POST /files/images | 上传图片 |
| 文章 | POST /articles、PUT /articles/{id}、DELETE /articles/{id} | 新建（可存为草稿）、修改、删除 |
| 文章 | PUT /articles/{id}/draft | 自动保存草稿 |
| 文章 | GET /articles/submit-token、POST /articles/{id}/publish | 防重复提交令牌；发布 |
| 文章 | GET /articles/{id}、GET /articles?sort=latest\|hot&categoryId=&tagId= | 详情；列表 |
| 分类标签 | GET /categories、GET /tags?keyword= | |
| 互动 | POST、DELETE /articles/{id}/like；POST、DELETE /articles/{id}/collect | |
| 评论 | GET /articles/{id}/comments?cursor=、POST /articles/{id}/comments、DELETE /comments/{id}、POST、DELETE /comments/{id}/like | |
| 关注 | POST、DELETE /users/{id}/follow；GET /feed?cursor= | |
| 通知 | GET /notifications?type=&cursor=、GET /notifications/unread-count、POST /notifications/read、GET /notifications/stream | stream 为 SSE |
| 排行 | GET /rank/active?period=day\|week\|month、GET /rank/hot-articles | |
| 搜索 | GET /search?q=&page= | |
| 管理 | GET /admin/dashboard | 看板 |
| 管理 | GET /admin/articles?status=、PUT /admin/articles/{id}/approve\|reject\|offline\|restore | 审核与下架 |
| 管理 | GET /admin/users、PUT /admin/users/{id}/ban\|unban | |
| 管理 | 分类、标签、敏感词的增删改查 | |
| 管理 | GET /admin/dead-letters、POST /admin/dead-letters/{id}/replay | 死信 |
| 管理 | GET /admin/audit-logs | 操作日志 |
| 管理 | POST /admin/search/reindex | 全量重建索引 |
| 管理 | GET、PUT /admin/dev/strategies | 仅 dev 环境 |

## 11. 前端页面

**用户端**
- `/` 首页：
  - "最新、热门、关注"三个标签页，可按分类筛选，无限滚动；
  - 右侧栏放活跃作者榜和热门文章榜。
- `/article/:id` 详情：Markdown 渲染和目录、点赞收藏按钮和计数、两级评论（加载更多）、作者卡片和关注按钮。
- `/editor`、`/editor/:id` 编辑器：标题、摘要、封面上传、分类、标签、Markdown 编辑和预览；每 30 秒自动保存草稿；发布时带防重复提交令牌。
- `/search?q=` 搜索结果：关键词高亮。
- `/user/:id` 个人主页：资料和统计，标签页：文章、收藏、关注、粉丝；本人可编辑资料。
- `/notifications` 通知：按类型分标签页，可标记已读。页头的未读角标通过 SSE 实时更新。
- `/rank` 排行：活跃榜（日、周、月）和热门文章。
- `/login`、`/register`、`/settings`（资料、头像、修改密码）。

**管理端**（`/admin`，独立布局）
- 数据看板（ECharts 折线图）、内容审核、文章管理、用户管理、分类标签、敏感词、死信消息、操作日志、实验开关（仅 dev）。

## 12. 配置与运行

### 12.1 环境变量（`.env.example` 列全，带说明）

| 用途 | 变量 |
|---|---|
| MySQL | MYSQL_ROOT_PASSWORD、MYSQL_DATABASE、MYSQL_USER、MYSQL_PASSWORD |
| Redis | REDIS_PASSWORD |
| RabbitMQ | RABBITMQ_USER、RABBITMQ_PASSWORD |
| MinIO | MINIO_ROOT_USER、MINIO_ROOT_PASSWORD |
| 认证 | JWT_SECRET（至少 32 字节的随机串） |
| 管理员 | ADMIN_USERNAME、ADMIN_PASSWORD |
| 搜索 | SEARCH_ENABLED |

### 12.2 Spring profile

- `dev`：后端在本机跑，连接 Compose 里的中间件；
- `docker`：后端和中间件全部跑在 Compose 里；
- `test`：用 Testcontainers 启动依赖。

### 12.3 Compose 服务和端口

| 服务 | 端口 | 默认启动 |
|---|---|---|
| frontend（Nginx） | 8080 | 是 |
| backend | 8081 | 是 |
| mysql | 3306 | 是 |
| redis | 6379 | 是 |
| rabbitmq | 5672、15672 | 是 |
| minio | 9000、9001 | 是 |
| elasticsearch | 9200 | 否，属于 `search` profile |
| prometheus、grafana | 9090、3000 | 否，属于 `monitoring` profile |

- 每个服务都配健康检查；backend 用 `condition: service_healthy` 等依赖就绪后再启动。
- MinIO 启动后由初始化脚本自动建 bucket。

### 12.4 常用命令（写进 README）

- 默认启动：`docker compose up -d`
- 带搜索：`docker compose --profile search up -d`
- 后端双实例：`docker compose up -d --scale backend=2`，Nginx upstream 指向服务名
- 本机跑后端：`./mvnw -pl xxx-web -am spring-boot:run -Dspring-boot.run.profiles=dev`
- 前端开发：`npm run dev`，Vite 把 `/api` 代理到 8081

### 12.5 Windows 注意事项

- 用 `.gitattributes` 让脚本统一用 LF 换行；
- Docker Desktop 内存不少于 6 GB；
- ES 的 `vm.max_map_count` 需要调大；
- 启动前检查端口是否被占用。

## 13. 测试与 CI

### 13.1 单元测试

计数策略、Feed 游标和归并、前缀树、限流的 key 构造、热度分计算、JWT 工具。

### 13.2 集成测试（Testcontainers）

- **计数并发**：200 个线程各读同一篇文章 5 次。预期：naive 少于 1000；atomic-sql 等于 1000；redis-batch 写回之后等于 1000。
- **消息可靠性**：
  - 消费者抛异常时，消息依次进入各级重试队列，最后落进死信表；
  - 同一个 msgId 投两次，只产生一条通知；
  - RabbitMQ 重启后，outbox 中继能把消息补发出去。
- **Feed**：三种模式下连续翻页不重复、不遗漏；取关、删文后相关文章不再出现。
- **缓存**：更新后能读到新值；空值缓存生效；广播之后两个实例的本地缓存都被清掉。
- **鉴权**：令牌过期、刷新令牌轮换、封禁即时生效。

### 13.3 前端

`vue-tsc` 类型检查 + 构建。

### 13.4 CI

GitHub Actions 分 backend、frontend 两个 job，每次 push 和 PR 都跑。

## 14. 造数与压测

### 14.1 造数

在 `xxx-web` 的 `datagen` profile 里写一个 CommandLineRunner：

| 数据 | 规模 |
|---|---|
| 用户 | 1 万，统一测试密码 |
| 文章 | 5 万，正文随机 500–2000 字 |
| 关注关系 | 30 万，其中 3–5 个大 V 各有 5000 以上粉丝 |
| 点赞、收藏、评论 | 若干 |

- 用 JDBC 批量插入，连接参数加 `rewriteBatchedStatements=true`。
- 造完后重建 ES 索引，并预热榜单。

### 14.2 压测场景（放在 `perf/plans/`）

1. **热点文章详情**：90% 的请求集中在 10 篇文章上，对比缓存的 none、redis、two-level 三种模式。
2. **并发阅读同一篇文章**：对比三种计数策略的计数准确性、数据库写入次数（取 `SHOW GLOBAL STATUS` 中 `Com_update` 的差值）、接口耗时。
3. **读取关注动态**：分普通用户和关注了大 V 的用户，对比 push、pull、hybrid 三种模式的读取耗时。
4. **大 V 发文**：对比 push、hybrid 两种模式的扇出完成时间和 Redis 内存占用。
5. **限流**：点赞接口超限时返回 429 的比例。

### 14.3 压测方法

- 固定数据，先预热；每组跑 3 次，取中位数。
- 记录机器配置（CPU、内存、分给 Docker 的资源）和 JMeter 线程数。
- 结果写进 `docs/perf-report.md`，包括表格和截图，并如实注明"本机测试，只看相对变化"。

### 14.4 双实例演示

用 `--scale backend=2` 起两个后端实例，验证三件事：
- 两级缓存的失效广播；
- SSE 能跨实例推送；
- 定时任务只在一个实例上执行。

## 15. 里程碑与完成标准

每个里程碑结束都要做到：测试全部通过；`docker compose up -d` 能正常运行；README 和三份学习文档已更新；已提交。

### M0 骨架

**内容**
- 多模块工程、父 POM、Maven Wrapper；
- Compose 编排 MySQL、Redis、RabbitMQ、MinIO（ES 放在 `search` profile），全部带健康检查；
- 统一返回、全局异常、参数校验、traceId 日志、Long 序列化为字符串；
- Flyway V1 建出全部表；
- springdoc 接口文档；
- 前端骨架：布局、页头、路由、Axios 封装、登录页占位、管理端布局占位；
- GitHub Actions；
- `.gitignore`、`.gitattributes`、`.env.example`；
- README 初版。

**完成标准**
- `docker compose up -d` 后，http://localhost:8080 能打开前端；
- `/api/health` 返回正常；
- `/swagger-ui.html` 能访问；
- CI 通过。

### M1 账号与文章

**内容**
- 注册、登录、刷新、退出、失败锁定；
- 资料修改、头像上传（MinIO）；
- 文章的增删改、草稿自动保存、防重复提交、分类和标签；
- 最新列表、详情；
- 前端页面：登录、注册、首页（最新）、详情、编辑器、设置。

**完成标准**
- 鉴权和文章的集成测试通过；
- 在浏览器里能走通"注册 → 写文章 → 发布 → 查看"。

### M2 互动与计数

**内容**
- 点赞、收藏、两级评论、评论点赞；
- article_stat 和 user_stat；
- 三种计数策略和计数写回任务；
- 个人主页的各个标签页。

**完成标准**
- 计数并发测试的结果符合 13.2 节的预期；
- 页面上的计数显示正确。

### M3 关注、通知、Feed

**内容**
- 关注和取关，关注列表、粉丝列表；
- outbox 本地消息表、中继任务、MQ 拓扑、重试队列、死信表；
- 通知的消费和幂等、通知页、SSE 推送未读数（跨实例）；
- Feed 的三种模式，以及游标分页、关注回填、冷启动；
- 首页的"关注"标签页。

**完成标准**
- 消息可靠性和 Feed 的集成测试通过；
- 在浏览器里能看到通知实时到达，Feed 能正常翻页。

### M4 排行、搜索、缓存、限流、敏感词

**内容**
- 活跃榜（日、周、月）和归档、热门文章；
- ES 索引、同步、高亮、关闭 ES 时的降级；
- 三种缓存模式、三大问题的处理、MQ 补偿删除；
- 限流注解；
- 前缀树敏感词，以及文章的待审核流程。

**完成标准**
- 各项功能都有对应的测试并通过；
- 关掉 ES 后搜索能自动降级；
- 超过限流阈值时返回 429。

### M5 管理端

**内容**
- 看板（日活用 HyperLogLog，加 daily_stat）、审核、文章、用户、分类标签、敏感词、死信重放、操作日志、实验开关；
- 管理员账号初始化。

**完成标准**
- 管理端的每个页面都能操作，封禁和下架实时生效；
- 死信能重放成功。

### M6 造数、压测、文档

**内容**
- 造数；
- 编写 JMeter 计划并跑完 14.2 节的全部场景；
- 双实例演示；
- `docs/perf-report.md`；
- README 定稿：
  - 用 Mermaid 画架构图、ER 图，以及两张时序图：一张是"发文 → Feed 扇出"，另一张是"点赞 → outbox → 通知"；
  - 说明运行方式、实验方法，并注明参考了技术派。

**完成标准**
- 压测报告里每个场景都有数据和结论；
- README 能指导一个新人从零跑起项目。

## 16. 学习文档要求

- **`docs/decisions.md`**：每条记录包括编号和标题、背景、方案、备选方案、为什么选它、代价和风险、相关代码位置。
- **`docs/pitfalls.md`**：每条记录包括编号、现象、原因、解决办法、教训、相关提交。
- **`docs/learn/M<n>.md`**：每个里程碑写一份，包含：
  1. 这个里程碑做了什么，一段话说清；
  2. 知识点清单：每个知识点写清楚它是什么、在项目里的哪个位置、怎么亲手验证（改配置、下断点、跑某个测试，不需要写代码）；
  3. 关键代码导读：按阅读顺序列出"文件:行号"，每处一句话说明；
  4. 面试可能怎么问：5–10 个问题，每个附答题要点（先讲原理，再联系本项目，最后讲局限）。

## 17. 约定与禁止事项

### 17.1 约定

- **Git**：开工时执行 `git init`。`.gitignore` 排除 target、node_modules、.env、日志和压测结果里的大文件；`.gitattributes` 设 `* text=auto eol=lf`。提交信息用中文，例如"M1: 完成账号与文章"。每个里程碑至少提交一次。
- **代码**：标识符用英文，注释和文档用中文；方法保持短小，不写没有信息量的注释。
- **密钥**：只放在 `.env`，仓库里只提交 `.env.example`。

### 17.2 禁止

- 复制技术派的代码；
- 测试里连接真实的外部服务；
- 把实验用的 naive、pull、none 设成默认策略。默认值应为 redis-batch、hybrid、two-level；
- 未经用户同意扩大范围。
