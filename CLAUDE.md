# code-nest（码巢）

开发者社区，参考技术派自己写的精简版。技术栈：Java 17 + Spring Boot 3.5 + Vue 3。完整规格见 `docs/SPEC.md`。

## 开工须知

- **每次进入先读 `docs/ROADMAP.md`**：M0–M6 已全部完成（2026-10-22），现在的工作是让用户读懂项目、在本机补真实数据、准备面试。ROADMAP 记录了当前状态、已定决策和任务清单，从第一个没勾的任务接着做；完成一项就在 ROADMAP 里勾掉并写上提交号。
- **SPEC 的地位**：`docs/SPEC.md` 仍是功能边界的依据；其中按里程碑推进的部分已经做完，后续以 ROADMAP 为准。正式名称 `code-nest`、GitHub 用户名 `qq3939` 已确认并完成替换（D-027）。
- **Git**：每项任务单独提交；不改写历史（不 rebase、不 amend 已推送的提交、不 force push）；推送前先确认用户同意。
- **范围**：不做 SPEC 第 3.2 节列出的功能。要加重要依赖或改动功能边界，先问用户。

## 关于用户

- 大二学生，靠读代码、读文档学习，除算法外不自己写代码。
  - 代码注释写"为什么"；
  - 每个里程碑写一份 `docs/learn/M<n>.md`；
  - 选型记进 `docs/decisions.md`，踩坑记进 `docs/pitfalls.md`。
- 回复用简体中文，代码和命令保持英文。

## 环境

- Windows + Docker Desktop（WSL2 后端）、JDK 17（Corretto）、Node.js LTS。本机默认的 `java` 是 25，构建前把 `JAVA_HOME` 指向 `C:\Program Files\Amazon Corretto\jdk17.0.18_9`。Docker Desktop 4.96.3 于 2026-10-23 装好（WSL2 后端，Docker 可用内存约 7.5 GiB；Windows 上的坑见 pitfalls P-019 至 P-021）。
- 端口：

  | 服务 | 端口 |
  |---|---|
  | 前端 | 8080 |
  | 后端 | 8081 |
  | MySQL | 3306 |
  | Redis | 6379 |
  | RabbitMQ | 5672、15672 |
  | MinIO | 9000、9001 |
  | ES | 9200 |

- 密钥只放在 `.env` 里，仓库只提交 `.env.example`。

## 常用命令

```bash
# 全部启动（首次加 --build）；带搜索加 --profile search，带监控加 --profile monitoring
docker compose up -d --build
# 双实例演示（之后 docker compose restart frontend）
docker compose up -d --build --scale backend=2

# 本机跑后端（先 docker compose up -d mysql redis rabbitmq minio minio-init）
cd backend && ./mvnw -pl code-nest-web -am spring-boot:run -Dspring-boot.run.profiles=dev

# 测试：单元 + 集成（集成测试需要 Docker）；只跑一个集成测试
cd backend && ./mvnw -B verify
cd backend && ./mvnw -B -pl code-nest-web -am verify -Dit.test=AuthIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false

# 前端开发（Vite 把 /api 代理到 8081）与构建
cd frontend && npm ci && npm run dev
cd frontend && npm run build

# 造数（跑完自动退出）→ 带 perf profile 启动后端 → 压测
cd backend && ./mvnw -B -DskipTests package
java -jar backend/code-nest-web/target/app.jar --spring.profiles.active=dev,datagen
java -Xmx1g -jar backend/code-nest-web/target/app.jar --spring.profiles.active=dev,perf
bash perf/prepare.sh && bash perf/run.sh all
```

注意：`java -jar` 运行中的 jar 不要被 `mvn package` 覆盖（P-014）；dev profile 从仓库根目录读取 `.env`，所以 `java -jar` 要在 `backend/code-nest-web` 或仓库根目录下执行。

## 参考

业务设计参考技术派（https://github.com/itwanger/paicoding ，`springboot3&vue3` 分支），不复制它的代码。
