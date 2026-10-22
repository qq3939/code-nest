# project-a（正式名称待定）

开发者社区，参考技术派自己写的精简版。技术栈：Java 17 + Spring Boot 3.5 + Vue 3。完整规格见 `docs/SPEC.md`。

## 开工须知

- **第一次进入本项目**：先通读 `docs/SPEC.md`，再向用户确认 SPEC 第 1 节列出的信息（项目正式名称、GitHub 用户名），然后从 M0 开始。
- **之后每次进入**：先看 README 和 `git log`，确认做到了哪个里程碑，从中断的地方接着做。
- **推进方式**：按 SPEC 第 15 节的里程碑顺序推进。每个里程碑达到完成标准后，依次：
  1. 跑测试；
  2. `docker compose up` 验证；
  3. 更新 README 和学习文档；
  4. 提交；
  5. 向用户汇报；
  6. 继续下一个。
- **范围**：不做 SPEC 第 3.2 节列出的功能。要加重要依赖或改动功能边界，先问用户。

## 关于用户

- 大二学生，靠读代码、读文档学习，除算法外不自己写代码。
  - 代码注释写"为什么"；
  - 每个里程碑写一份 `docs/learn/M<n>.md`；
  - 选型记进 `docs/decisions.md`，踩坑记进 `docs/pitfalls.md`。
- 回复用简体中文，代码和命令保持英文。

## 环境

- Windows + Docker Desktop（WSL2 后端）、JDK 17（Corretto）、Node.js LTS。
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
cd backend && ./mvnw -pl project-a-web -am spring-boot:run -Dspring-boot.run.profiles=dev

# 测试：单元 + 集成（集成测试需要 Docker）；只跑一个集成测试
cd backend && ./mvnw -B verify
cd backend && ./mvnw -B -pl project-a-web -am verify -Dit.test=AuthIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false

# 前端开发（Vite 把 /api 代理到 8081）与构建
cd frontend && npm ci && npm run dev
cd frontend && npm run build

# 造数（跑完自动退出）→ 带 perf profile 启动后端 → 压测
cd backend && ./mvnw -B -DskipTests package
java -jar backend/project-a-web/target/app.jar --spring.profiles.active=dev,datagen
java -Xmx1g -jar backend/project-a-web/target/app.jar --spring.profiles.active=dev,perf
bash perf/prepare.sh && bash perf/run.sh all

# 正式名称确定后替换占位名
bash scripts/rename-project.sh <项目名> <GitHub 用户名>
```

注意：`java -jar` 运行中的 jar 不要被 `mvn package` 覆盖（P-014）；dev profile 从仓库根目录读取 `.env`，所以 `java -jar` 要在 `backend/project-a-web` 或仓库根目录下执行。

## 参考

业务设计参考技术派（https://github.com/itwanger/paicoding ，`springboot3&vue3` 分支），不复制它的代码。
