# 企业工单多智能体处理平台

多 Agent 协同自动处理企业 IT 工单：**规划 Agent** 拆解分类工单，**检索 Agent** 查运维手册（RAG）和历史相似工单，**回复 Agent** 生成回复草稿并给出处置建议，支持自动回复、人工审核、转人工的完整业务闭环，含状态机流转审计与失败重试。

**技术栈**：Java 21 · Spring Boot 3.5 · Spring Data JPA · H2/PostgreSQL · Vue 3 · Vite · Element Plus · ECharts

![技术架构](https://img.shields.io/badge/后端-Spring%20Boot%203.5-6DB33F) ![前端](https://img.shields.io/badge/前端-Vue3%20%2B%20Element%20Plus-42b883) ![Agent](https://img.shields.io/badge/Agent-多智能体流水线-blue)

## 架构

```
                 ┌─────────────── Vue3 + Element Plus 管理端 ───────────────┐
                 │  仪表盘(统计图表)   工单管理(审核/重试)   知识库(导入/检索) │
                 └──────────────────────────┬───────────────────────────────┘
                                            │ REST API（构建产物打进同一个 jar）
邮件/页面 提交工单                           ▼
      │        Spring Boot 后端
      ▼
 NEW ──► TRIAGING ──► RETRIEVING ──► DRAFTING ──┬─► HUMAN_REVIEW ─► RESOLVED
        (规划Agent)    (检索Agent)    (回复Agent)  ├─► RESOLVED(自动发邮件)
            │            │              │         └─► ESCALATED ──► RESOLVED(人工关闭)
            └────────────┴──────────────┴──► FAILED(记录失败阶段，支持断点重试)
```

- **规划 Agent（TriageAgent）**：输出类别/优先级/处理组/处理步骤（LLM 结构化输出）
- **检索 Agent（RetrievalAgent）**：知识库 RAG 检索 + 历史已解决工单相似案例检索
- **回复 Agent（ReplyAgent）**：综合分诊与检索结果生成回复草稿，决策 AUTO_REPLY / NEED_HUMAN / ESCALATE
- **编排器（TicketPipeline）**：单线程顺序执行，每阶段独立事务（REQUIRES_NEW），失败落 FAILED 并记录失败阶段；重试从断点续跑，已完成阶段不重算
- **LLM 双模式**：`mock`（内置规则引擎，无需 API Key）/ `openai`（任意 OpenAI 兼容接口，含 3 次指数退避重试、4xx 不重试）

## 快速开始

要求：JDK 21+、Maven 3.6.3+、Node 16+（仅前端开发时需要）。仓库已包含前端构建产物，**后端一条命令即可跑起完整系统**：

```bash
cd ticket-agent-platform
mvn -DskipTests package
java -jar target/ticket-agent-platform-0.1.0-SNAPSHOT.jar
# 打开 http://127.0.0.1:8080 （前端+后端同一个进程）
```

首次启动自动灌入 **默认管理员 + 6 篇运维手册 + 6 条历史工单**。

### 登录与权限

- **管理员**：用户名 `admin`，密码 `admin123`（系统预置，注册接口不开放管理员角色——即使请求里带 `role=ADMIN` 也会被强制为员工）
- **员工**：登录页点「注册员工账号」自助注册

| 能力 | 员工 EMPLOYEE | 管理员 ADMIN |
|---|---|---|
| 提交工单（触发 Agent 流水线） | ✅ | ✅ |
| 查看工单 | 仅自己提交的 | 全量 |
| 仪表盘统计 | 仅自己的数据（scope=MINE） | 全局（scope=ALL） |
| 人工审核/驳回回复草稿 | ❌ 403 | ✅ |
| 失败工单断点重试 / 关闭转人工工单 | ❌ 403 | ✅ |
| 知识库导入/检索（页面与接口） | ❌ 403 | ✅ |

技术实现：Spring Security 无状态会话 + 手写 HS256 JWT（零第三方依赖）+ `@PreAuthorize` 方法级权限 + 前端路由守卫与按角色渲染；密码 BCrypt 哈希存储；提单人信息取自登录态、后端写入，客户端不可伪造。

### 前端开发模式（热更新）

```bash
cd frontend
npm install --registry=https://registry.npmmirror.com   # 首次
npm run dev        # 开发服务器 http://localhost:5173，/api 自动代理到 8080
npm run build      # 构建产物直接输出到后端 static 目录，重新打包即可
```

### 接入真实大模型（任意 OpenAI 兼容接口）

```bash
export LLM_API_KEY=你的key
java -jar target/*.jar --app.llm.mode=openai --app.llm.model=glm-4.6
```

DeepSeek：`--app.llm.base-url=https://api.deepseek.com`；本地 Ollama：`--app.llm.base-url=http://localhost:11434/v1`。

### 换数据库：MySQL / PostgreSQL

默认用嵌入式 H2（零配置）；三选一，只差一个启动参数：

```bash
# 本机 MySQL（需先建库: CREATE DATABASE ticketdb DEFAULT CHARACTER SET utf8mb4;）
java -jar target/*.jar --spring.profiles.active=mysql
#   连接默认 root/123456，可用 DB_USER / DB_PASSWORD 环境变量覆盖

# PostgreSQL
java -jar target/*.jar --spring.profiles.active=postgres
```

切换后是全新空库（自动建表+重新灌种子数据），H2 的旧数据文件 `data/ticketdb.mv.db` 仍保留、互不影响；去掉启动参数即回到 H2。Windows 下也可直接双击 `start.bat`（H2）或 `start-mysql.bat`（MySQL）。

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tickets` | 创建工单，自动触发流水线 |
| GET | `/api/tickets` / `/{id}` | 列表 / 详情（含事件时间线） |
| POST | `/api/tickets/{id}/retry` | 失败重试（从失败阶段断点续跑） |
| POST | `/api/tickets/{id}/review` | 人工审核 `{approve, comment}` |
| POST | `/api/tickets/{id}/close` | 转人工工单处理完毕关闭 |
| GET | `/api/tickets/stats` | 状态/类别分布统计 |
| POST | `/api/knowledge/manual` | 导入运维手册 `{title, content}`（自动切块） |
| GET | `/api/knowledge/manuals` / `/search?q=` | 手册列表 / 检索调试 |
| GET | `/api/meta` | 运行元信息（LLM 模式等） |
| POST | `/api/auth/register` | 员工自助注册（角色强制 EMPLOYEE） |
| POST | `/api/auth/login` | 登录，返回 JWT + 用户信息 |
| GET | `/api/auth/me` | 当前登录人信息 |

## 目录结构

```
ticket-agent-platform/
├── src/main/java/com/ticketplatform/
│   ├── agent/        三个 Agent（Triage/Retrieval/Reply）+ 结果模型
│   ├── pipeline/     TicketPipeline 编排器（状态流转+失败捕获+断点重试）
│   ├── domain/       Ticket / TicketStatus(状态机) / TicketEvent(审计) / KnowledgeChunk
│   ├── llm/          OpenAI 兼容客户端（重试）+ JSON 容错解析
│   ├── service/      知识库RAG / 状态流转服务(统一入口) / 邮件 / 种子数据
│   ├── security/     JWT(HS256手写) / 认证过滤器 / Security配置 / 当前用户
│   ├── web/          REST 控制器 + 全局异常处理 + 认证接口
│   └── repo/         Spring Data JPA 仓库
├── src/main/resources/static/   前端构建产物（npm run build 输出）
└── frontend/                    Vue3 前端源码（Vite + Element Plus + ECharts）
    └── src/views/               Dashboard / Tickets / Knowledge / Login / Register
```

## 技术亮点与设计取舍

- **多智能体编排**：规划→检索→回复三段式 Agent 流水线，每阶段独立事务（REQUIRES_NEW），任一阶段失败落 FAILED 并支持**断点续跑**（已完成阶段结果持久化复用，不重算）
- **状态机驱动的业务闭环**：工单 8 状态全流转经统一入口校验（非法流转直接拒绝），每次变化写审计事件表，前端时间线完整回放"谁在何时做了什么"
- **LLM 工程化**：OpenAI 兼容协议适配任意模型（GLM/DeepSeek/Qwen/Ollama）；指数退避重试 + 4xx 快速失败；结构化输出容错解析；**Mock 模式**让系统无 API Key 也能完整演示（这也是单测/演示的依赖隔离手段）
- **RAG 检索**：手册自动切块（固定窗口+重叠），中文 bigram 分词相似度检索，历史工单案例检索为回复提供依据；接口抽象预留 pgvector/ES 升级路径
- **人机协同知识回流**：每张已解决工单都落 `resolution`（最终处理方案）字段——自动回复的存回复内容、人工审核的存所发草稿、**转人工的存管理员关闭时必填的处理方案**；检索 Agent 优先用它做相似工单匹配，人工处理经验越多，Agent 后续回答越准
- **不满意重开闭环**：提单人对已解决工单不满意可一键重开转人工（必填原因，仅限本人或管理员），人工纠正后的新方案覆盖旧方案回流知识库——**错误答案会被持续修正**，状态机支持 RESOLVED→ESCALATED→RESOLVED 循环且全程留痕
- **登录鉴权与角色权限**：JWT 无状态认证 + 员工/管理员双角色数据隔离与操作权限（前后端双重防护），密码 BCrypt 存储
- **工程完整度**：前后端分离开发、同进程部署（前端构建产物打进 jar，演示零依赖）；H2 零配置起步、Profile 切 PostgreSQL；全局异常处理 + 参数校验

## 升级路线（按需）

1. **真实邮件**：新增 `MailSender` 实现用 `spring-boot-starter-mail`，或给现有类加 `@Primary`
2. **向量检索**：`KnowledgeBaseService.search()` 换 pgvector / Elasticsearch，接口不变
3. **Spring AI**：`LlmClient` 换成 Spring AI 的 `ChatClient` 实现，Agent 层不动
4. **收票渠道**：新增 IMAP 收件轮询或 Chatwoot Webhook 接入 `TicketController`
5. **并发**：`TicketPipeline` 的单线程执行器换线程池，按工单 ID 分片保证顺序
6. **登录鉴权**：加 Spring Security + JWT，前端路由守卫

## 已知限制（MVP 范围）

- mock 模式是关键词规则引擎，会误判——接入真实 LLM 即解决（一条启动参数切换）
- 知识库检索是字符 bigram 重叠度打分，不是语义向量检索
- 流水线单线程顺序执行；JWT 默认密钥需生产替换（`JWT_SECRET` 环境变量）
