# 学习文档索引

每个里程碑一份，按顺序读：

| 文档 | 内容 |
|---|---|
| [M0.md](M0.md) | 工程骨架：多模块、统一返回、全局异常、traceId、Long 精度、Flyway、Compose |
| [M1.md](M1.md) | 账号与文章：JWT 双令牌、失败锁定、封禁实时生效、防重复提交、MinIO 上传 |
| [M2.md](M2.md) | 互动与计数：关系表幂等开关、三种计数策略、写回与对账 |
| [M3.md](M3.md) | 关注、通知、Feed：outbox、MQ 重试与死信、幂等消费、SSE、推拉结合 |
| [M4.md](M4.md) | 排行、搜索、缓存、限流、敏感词 |
| [M5.md](M5.md) | 管理端：HyperLogLog 日活、审核、封禁、死信重放、实验开关 |
| [M6.md](M6.md) | 造数、压测、双实例演示 |
| [knowledge-map.md](knowledge-map.md) | 知识地图：后端八股的每个主题在本项目的哪里、哪份学习文档讲过、原理去哪里看，读每个里程碑时对照着看 |

## 路径缩写

"关键代码导读"里的路径都做了缩写，对应关系如下：

| 缩写 | 实际目录 |
|---|---|
| `api/…` | `backend/code-nest-api/src/main/java/io/github/qq3939/codenest/api/…` |
| `core/…` | `backend/code-nest-core/src/main/java/io/github/qq3939/codenest/core/…` |
| `service/…` | `backend/code-nest-service/src/main/java/io/github/qq3939/codenest/service/…` |
| `web/…` | `backend/code-nest-web/src/main/java/io/github/qq3939/codenest/web/…` |
| `IT/…` | `backend/code-nest-web/src/test/java/io/github/qq3939/codenest/web/…` |
| `lua/…` | `backend/code-nest-*/src/main/resources/scripts/…` |
| `fe/…` | `frontend/src/…` |

## 引用的写法

代码引用写成 `` `路径:行号`（`锚点`） ``，锚点是那一行附近的函数名、类名、字段名或配置键，后面可以接说明，例如 `` `service/cache/CacheService.java:105`（`get`，读路径） ``。同一行里再引用同一个文件时可以只写 `` `:行号` ``。只写路径、不写行号的引用表示整个文件。

CI 会运行 `python3 scripts/check-learn-refs.py` 检查这些引用：文件存在且唯一、行号不越界、锚点在该行上下 3 行内。代码改动让行号偏移了，CI 会指出是哪一条、锚点现在在第几行；本机改完文档也可以先跑一遍（Windows 上用 `python`）。

## 怎么跑单个测试

```bash
cd backend
./mvnw -B -pl code-nest-web -am verify -Dit.test=AuthIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
```

集成测试需要本机 Docker 在运行（Testcontainers 会自动拉起 MySQL、Redis、RabbitMQ）。
