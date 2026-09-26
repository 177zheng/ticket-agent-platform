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

首次启动自动灌入 6 篇运维手册 + 6 条历史工单。

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

### 换 PostgreSQL

```bash
java -jar target/*.jar --spring.profiles.active=postgres
```

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

## 目录结构

```
ticket-agent-platform/
├── src/main/java/com/ticketplatform/
│   ├── agent/        三个 Agent（Triage/Retrieval/Reply）+ 结果模型
│   ├── pipeline/     TicketPipeline 编排器（状态流转+失败捕获+断点重试）
│   ├── domain/       Ticket / TicketStatus(状态机) / TicketEvent(审计) / KnowledgeChunk
│   ├── llm/          OpenAI 兼容客户端（重试）+ JSON 容错解析
│   ├── service/      知识库RAG / 状态流转服务(统一入口) / 邮件 / 种子数据
│   ├── web/          REST 控制器 + 全局异常处理
│   └── repo/         Spring Data JPA 仓库
├── src/main/resources/static/   前端构建产物（npm run build 输出）
└── frontend/                    Vue3 前端源码（Vite + Element Plus + ECharts）
    └── src/views/               Dashboard / Tickets / Knowledge 三个页面
```

## 简历 / 面试亮点

- **多智能体编排**：规划→检索→回复三段式 Agent 流水线，每阶段独立事务（REQUIRES_NEW），任一阶段失败落 FAILED 并支持**断点续跑**（已完成阶段结果持久化复用，不重算）
- **状态机驱动的业务闭环**：工单 8 状态全流转经统一入口校验（非法流转直接拒绝），每次变化写审计事件表，前端时间线完整回放"谁在何时做了什么"
- **LLM 工程化**：OpenAI 兼容协议适配任意模型（GLM/DeepSeek/Qwen/Ollama）；指数退避重试 + 4xx 快速失败；结构化输出容错解析；**Mock 模式**让系统无 API Key 也能完整演示（这也是单测/演示的依赖隔离手段）
- **RAG 检索**：手册自动切块（固定窗口+重叠），中文 bigram 分词相似度检索，历史工单案例检索为回复提供依据；接口抽象预留 pgvector/ES 升级路径
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
- 无登录鉴权、多租户；流水线单线程顺序执行
