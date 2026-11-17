# 法律案件办案智能体（Legal Case Assistant）

面向检察机关的 AI 办案质量评查系统。Java 后端负责文书管理、Dify 工作流编排与数据持久化；Python Agent 基于 LangGraph + RAG 完成多步推理，自动输出问题清单、法条依据、类案参考与改进建议。

> 仓库不包含业务数据（`src/main/resources/data/*.csv`）、本地密钥（`application-local.yml`、`.env`）和 Python 虚拟环境，克隆后请按下方步骤自行配置。

## 功能特性

- **电子卷宗上传与解析**：PDF / DOCX / DOC（JODConverter + LibreOffice 转换）
- **Dify 工作流分析**：同步接口与 SSE 流式进度返回
- **Agent 多步推理**：ReAct 循环完成 问题识别 → 法条检索 → 类案检索 → 综合报告
- **RAG 法律知识库**：LlamaIndex + PostgreSQL/pgvector，支持 Bge-M3 等 Embedding 模型
- **案件管理**：案件 CRUD、问题列表、检察官画像、类案查询
- **数据问答**：DuckDB 直接查询 CSV 业务数据
- **推理链追踪**：记录 Agent 每一步 reason / act / answer
- **接口文档**：Knife4j / OpenAPI 3

## 系统架构

```
┌──────────┐      ┌──────────────────────────────────────────────┐
│  前端     │─────→│ Java 后端 :8080 (Spring Boot 3)              │
└──────────┘      │  ├── 文件上传/下载（WebFlux + SSE）           │
                  │  ├── Dify 工作流调用                          │
                  │  ├── 案件 CRUD / 检察官画像 / 类案查询         │
                  │  └── Agent 调用入口                           │
                  └──────────────┬───────────────────────────────┘
                                 │ HTTP
                  ┌──────────────▼───────────────────────────────┐
                  │ Python Agent :8000 (FastAPI)                 │
                  │  ├── LangGraph ReAct 引擎                     │
                  │  ├── Tools：Dify / 案件 / 检察官 / 类案 / 法条 │
                  │  ├── RAG 法律知识库（LlamaIndex + PGVector）   │
                  │  └── Trace 推理链追踪                         │
                  └──────────────────────────────────────────────┘
```

一次分析的完整链路：上传文书 → 存储并上传 Dify → Dify 工作流初步分析 → 结果转发给 Python Agent → Agent 多步推理增强 → 返回结构化报告并入库。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Java 后端 | Java 17、Spring Boot 3.1.5、Spring Data JPA、Spring WebFlux、Spring Retry |
| 数据库 | MySQL 8、DuckDB（CSV 查询）、PostgreSQL + pgvector |
| 文档处理 | Apache POI、JODConverter / LibreOffice |
| AI 平台 | Dify（工作流 + 知识库） |
| Agent 服务 | Python 3.10+、FastAPI、LangGraph、LangChain、LlamaIndex |
| API 文档 | Knife4j 4.5 / OpenAPI 3 |

## 快速开始

### 1. 环境要求

- JDK 17、Maven 3.8+
- MySQL 8
- Python 3.10+
- PostgreSQL 14+（需安装 `pgvector` 扩展）
- Dify 平台（本地或远程）
- LibreOffice（可选，用于 `.doc` 转 `.pdf`）

### 2. 启动 Java 后端

```bash
# 创建数据库（默认库名 case_assistant_db2，可在配置中修改）
mysql -uroot -p -e "CREATE DATABASE case_assistant_db2 DEFAULT CHARSET utf8mb4;"

# 生成本地配置并填入真实值
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

# 启动
mvn spring-boot:run
```

- 服务地址：`http://localhost:8080`
- 接口文档：`http://localhost:8080/doc.html`
- 表结构由 `spring.jpa.hibernate.ddl-auto=update` 自动创建

### 3. 启动 Python Agent

```bash
cd case-assistant-agent

python -m venv .venv
source .venv/Scripts/activate        # Windows Git Bash；Linux/macOS 用 .venv/bin/activate

pip install -r requirements.txt

cp .env.example .env                 # 填入 LLM、Embedding、PostgreSQL 等配置
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

- 健康检查：`http://localhost:8000/health`
- 接口文档：`http://localhost:8000/docs`

### 4. 准备业务数据（可选）

数据问答功能依赖 `src/main/resources/data/` 下的 CSV 文件（因含业务信息未随仓库提供）：

| 文件 | 用途 |
| --- | --- |
| `aj_yx_aj.csv` | 案件信息 |
| `aqzy.csv` | 案情摘要 |
| `zlpc_yx_aj.csv` | 质量评查结果 |
| `zlpc_czjl.csv` | 评查操作记录 |
| `pcmb.csv` | 评查模板 |

将文件放入该目录后重启后端，`POST /api/data-query/ask` 才会返回数据。

## 配置项

真实值放在被 `.gitignore` 排除的 `src/main/resources/application-local.yml`，生产环境建议使用环境变量覆盖：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 激活的 Profile | `local` |
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://localhost:3306/case_assistant_db2...` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | 空 |
| `DIFY_BASE_URL` | Dify API 地址 | `http://localhost/v1` |
| `DIFY_CHAT_KEY` | Dify 对话应用 Key | 空 |
| `DIFY_WORKFLOW_KEY` | Dify 工作流应用 Key | 空 |
| `DIFY_TEXT_TO_SQL_KEY` | Dify Text-to-SQL 应用 Key | 空 |
| `LCBA_BASE_URL` | 检察业务系统接口地址 | 空 |
| `FILE_STORAGE_PATH` | 上传文件存储目录 | `D:/case_files` |
| `AGENT_SERVICE_URL` | Python Agent 地址 | `http://localhost:8000` |

Python Agent 的配置见 `case-assistant-agent/.env.example`。

## 主要接口

### Java 后端（`http://localhost:8080`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/analysis/start` | 上传文件并发起分析 |
| POST | `/api/analysis/from-client-platform` | 从业务平台发起分析 |
| GET | `/api/analysis/stream-from-reports` | SSE 流式分析进度 |
| GET | `/api/cases/{caseId}` | 案件详情 |
| PUT | `/api/cases/{caseId}` | 更新案件 |
| GET | `/api/cases/search` | 案件搜索 |
| GET | `/api/reports` | 报告列表 |
| GET | `/api/prosecutors/summary` | 检察官汇总 |
| GET | `/api/prosecutors/{id}/profile` | 检察官画像 |
| GET | `/api/prosecutors/{id}/problems` | 检察官问题列表 |
| POST | `/api/data-query/ask` | 数据问答（DuckDB） |
| POST | `/api/agent/analyze` | 调用 Agent 深度分析 |
| POST | `/api/agent/chat` | Agent 对话 |
| GET | `/api/agent/health` | Agent 健康检查 |

### Python Agent（`http://localhost:8000`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/analyze` | 案件深度分析 |
| POST | `/api/agent/chat` | 多轮对话 |
| POST | `/api/agent/rag/build-index` | 构建法律知识库索引 |
| POST | `/api/agent/rag/search` | 法条/知识检索 |
| GET | `/api/agent/traces` | 推理链列表 |
| GET | `/api/agent/traces/{traceId}` | 推理链详情 |

## 目录结构

```
.
├── pom.xml
├── src/main/java/com/hubu/caseassistant/
│   ├── config/          # Dify、文件存储、Agent 服务配置
│   ├── controller/      # REST / SSE 接口
│   ├── dto/             # 请求与响应对象
│   ├── model/           # JPA 实体
│   ├── repository/      # 数据访问
│   ├── service/         # 业务逻辑与外部客户端
│   └── util/
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml.example
│   └── data.sql
├── src/test/
└── case-assistant-agent/    # Python Agent 服务
    ├── app/
    │   ├── agent/       # LangGraph 图、节点、Prompt
    │   ├── api/         # FastAPI 路由
    │   ├── rag/         # 加载、切分、索引、向量库
    │   ├── tools/       # Agent 工具集
    │   └── trace/       # 推理链存储
    ├── docs/legal/      # 法律知识库文档
    └── tests/
```

## 安全说明

- 所有密钥均通过环境变量注入，`application-local.yml`、`.env` 已被 `.gitignore` 排除；
- 仓库历史中不含真实密钥；
- 请勿将生产环境的数据库密码、API Key 提交到仓库；如曾经泄露，请立即在对应平台轮换。