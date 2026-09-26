# 企业工单多智能体处理平台（MVP）

多 Agent 协同自动处理企业 IT 工单：**规划 Agent** 拆解分类工单，**检索 Agent** 查运维手册（RAG）和历史相似工单，**回复 Agent** 生成回复草稿并给出处置建议，支持自动回复、人工审核、转人工的完整业务闭环，含状态机流转审计与失败重试。

## 架构

```
邮件/页面 提交工单
      │
      ▼
 NEW ──► TRIAGING ──► RETRIEVING ──► DRAFTING ──┬─► HUMAN_REVIEW ─► RESOLVED
        (规划Agent)    (检索Agent)    (回复Agent)  ├─► RESOLVED(自动发邮件)
            │            │              │         └─► ESCALATED ──► RESOLVED(人工关闭)
            └────────────┴──────────────┴──► FAILED(记录失败阶段，支持断点重试)
```

- **规划 Agent（TriageAgent）**：输出类别/优先级/处理组/处理步骤（结构化输出）
- **检索 Agent（RetrievalAgent）**：知识库 RAG 检索 + 历史已解决工单相似案例检索
- **回复 Agent（ReplyAgent）**：综合分诊与检索结果生成回复草稿，决策 AUTO_REPLY / NEED_HUMAN / ESCALATE
- **编排器（TicketPipeline）**：单线程顺序执行，每阶段独立事务（REQUIRES_NEW），失败落 FAILED 并记录失败阶段；重试从断点续跑，已完成阶段不重算
- **LLM 双模式**：`mock`（内置规则引擎，无需 API Key）/ `openai`（任意 OpenAI 兼容接口，含 3 次指数退避重试、4xx 不重试）

## 快速开始

要求：JDK 21+、Maven 3.6.3+（本工作区已下载便携版到 `../tools/`，无需系统安装）。

```bash
cd ticket-agent-platform

# 用工作区便携工具链构建运行（Windows Git Bash）
export JAVA_HOME="C:/Users/26348/.zcode/workspace/default/tools/jdk21"
../tools/maven/bin/mvn -q -DskipTests package
../tools/jdk21/bin/java -Dfile.encoding=UTF-8 -jar target/ticket-agent-platform-0.1.0-SNAPSHOT.jar
```

打开 http://127.0.0.1:8080 即可使用（首次启动自动灌入 6 篇运维手册 + 6 条历史工单）。

### 接入真实大模型（任意 OpenAI 兼容接口）

```bash
# 智谱 GLM
export LLM_API_KEY=你的key
../tools/jdk21/bin/java -Dfile.encoding=UTF-8 \
  -jar target/ticket-agent-platform-0.1.0-SNAPSHOT.jar \
  --app.llm.mode=openai --app.llm.model=glm-4.6
```

DeepSeek：`--app.llm.base-url=https://api.deepseek.com`，`--app.llm.model=deepseek-chat`；
本地 Ollama：`--app.llm.base-url=http://localhost:11434/v1`。

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
| GET | `/api/tickets/stats` | 各状态统计 |
| POST | `/api/knowledge/manual` | 导入运维手册 `{title, content}`（自动切块） |
| GET | `/api/knowledge/search?q=` | 知识库检索调试 |

## 目录结构

```
src/main/java/com/ticketplatform/
├── agent/        三个 Agent（Triage/Retrieval/Reply）+ 结果模型
├── pipeline/     TicketPipeline 编排器（状态流转+失败捕获+断点重试）
├── domain/       Ticket / TicketStatus(状态机) / TicketEvent(审计) / KnowledgeChunk
├── llm/          OpenAI 兼容客户端（重试）+ JSON 容错解析
├── service/      知识库RAG / 状态流转服务(统一入口) / 邮件 / 种子数据
├── web/          REST 控制器 + 全局异常处理
└── repo/         Spring Data JPA 仓库
src/main/resources/static/index.html   单页前端（列表/详情/审核/时间线）
```

## 升级路线（按需）

1. **真实邮件**：新增 `MailSender` 实现用 `spring-boot-starter-mail`，或给现有类加 `@Primary`
2. **向量检索**：`KnowledgeBaseService.search()` 换 pgvector / Elasticsearch，接口不变
3. **Spring AI**：`LlmClient` 换成 Spring AI 的 `ChatClient` 实现，Agent 层不动
4. **收票渠道**：新增 IMAP 收件轮询或 Chatwoot Webhook 接入 `TicketController`
5. **并发**：`TicketPipeline` 的单线程执行器换线程池，按工单 ID 分片保证顺序

## 已知限制（MVP 范围）

- mock 模式是关键词规则引擎，会误判（如"培训咨询"因含"电脑"被分类到终端设备）——接入真实 LLM 即解决
- 知识库检索是字符 bigram 重叠度打分，不是语义向量检索
- 无登录鉴权、多租户；流水线单线程顺序执行
