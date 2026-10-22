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

项目搭起来后在这里补充：启动、测试、前端开发、压测。

## 参考

业务设计参考技术派（https://github.com/itwanger/paicoding ，`springboot3&vue3` 分支），不复制它的代码。
