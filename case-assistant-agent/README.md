# 智慧检察平台 - 启动与使用文档

## 系统架构

```
┌─────────┐     ┌──────────────────────────────────────────────┐
│  前端    │────→│ Java 后端 (8080)                              │
└─────────┘     │  ├── 文件上传/下载                             │
                │  ├── Dify 工作流调用                           │
                │  ├── 案件 CRUD / 检察官画像                    │
                │  └── 类案查询 API                              │
                └──────────────┬───────────────────────────────┘
                               │ HTTP
                ┌──────────────▼───────────────────────────────┐
                │ Python Agent 服务 (8000)                      │
                │  ├── LangGraph Agent 引擎 (ReAct 循环)        │
                │  ├── 5 个 Tools                               │
                │  │   ├── trigger_dify_workflow                │
                │  │   ├── query_case_info                     │
                │  │   ├── query_prosecutor_info               │
                │  │   ├── search_similar_cases (类案检索)      │
                │  │   └── legal_doc_search (RAG 法条检索)     │
                │  ├── RAG 法律知识库 (LlamaIndex + PGVector)   │
                │  └── Trace 推理链追踪                         │
                └──────────────────────────────────────────────┘
```

---

## 完整工作流

### 一次案件深度分析的完整流程

```
步骤 1: 前端上传法律文书（PDF/DOCX）
        ↓
步骤 2: Java 后端接收文件
        ├── 保存到本地存储
        ├── 上传到 Dify 平台获取 fileId
        └── 调用 Dify 工作流进行初步分析
        ↓
步骤 3: Dify 返回初步分析结果（JSON）
        ├── case_name: 案件名称
        ├── undertaker: 承办人
        ├── issues: [{category: "证据采信", type: "瑕疵"}, ...]
        ├── case_summary: 案情摘要
        ├── issueAnalysisContent: 问题分析
        └── problem_description: 问题描述
        ↓
步骤 4: Java 后端将 Dify 结果转发给 Agent 服务
        POST /api/agent/analyze
        {
          "message": "请对这个案件进行深度质量分析",
          "difyResult": { ...Dify分析结果... }
        }
        ↓
步骤 5: Agent 自主执行多步推理
        ├── [reason] 理解 Dify 结果，识别具体问题
        │   例: "发现证据瑕疵 - 物证提取笔录缺失"
        │
        ├── [act] legal_doc_search("物证提取笔录 规定")
        │   → 返回: "根据《刑事诉讼法》第128条..."
        │
        ├── [act] search_similar_cases("证据采信", "瑕疵")
        │   → 返回: "2024年李某案，同类问题，处理结果..."
        │
        └── [answer] 综合输出结构化报告
        ↓
步骤 6: Agent 返回深度分析报告
        ├── 案件基本信息
        ├── 问题清单（每个问题附法条依据 + 类案参考）
        └── 综合评估与改进建议
        ↓
步骤 7: Java 后端返回给前端展示
```

### Agent 推理链示例

```json
{
  "trace_id": "abc-123",
  "total_tokens": 2456,
  "total_duration_ms": 8200,
  "steps": [
    {
      "step_number": 1,
      "thought": "Dify 结果显示该案存在证据采信瑕疵，具体是物证提取笔录缺失。我需要检索相关法律规定。",
      "tool_name": "legal_doc_search",
      "tool_input": {"query": "物证提取笔录 刑事诉讼法 规定"},
      "tool_output": "[1] (相关度: 0.89) 《刑事诉讼法》第128条：侦查人员对于与犯罪有关的场所...",
      "duration_ms": 1200
    },
    {
      "step_number": 2,
      "thought": "找到了法条依据。现在查询历史上类似问题的案件，了解处理方式。",
      "tool_name": "search_similar_cases",
      "tool_input": {"issue_category": "证据采信", "issue_type": "瑕疵"},
      "tool_output": "[1] 李某盗窃案\n  问题: 证据采信 - 瑕疵\n  处理结果: 补正...",
      "duration_ms": 800
    }
  ],
  "final_answer": "【案件质量深度分析报告】\n\n### 案件基本信息\n..."
}
```

---

## 一、环境准备

### 1.1 基础环境

| 组件 | 版本要求 | 用途 |
|------|---------|------|
| JDK | 17+ | Java 后端 |
| Maven | 3.8+ | Java 构建 |
| Python | 3.11+ | Agent 服务 |
| MySQL | 8.0+ | 业务数据库 |
| PostgreSQL | 15+ (需 pgvector 扩展) | 向量数据库（RAG） |
| LibreOffice | 7.0+ | doc 转 pdf |

### 1.2 外部服务

| 服务 | 默认地址 | 用途 |
|------|---------|------|
| Dify 平台 | http://localhost/v1 | AI 工作流引擎（初步分析） |
| OpenAI API | https://api.openai.com/v1 | LLM 推理 + Embedding 向量化 |

---

## 二、Java 后端启动

### 2.1 配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/case_assistant_db2?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: 你的密码

dify:
  api:
    base-url: http://localhost/v1          # Dify 地址
    chat-key: app-xxx                      # Dify 聊天 API Key
    work-flow-key: app-xxx                 # Dify 工作流 API Key

# Python Agent 服务地址
agent:
  service:
    url: http://localhost:8000
    timeout-seconds: 120
```

### 2.2 启动

```bash
cd case-assistant-system
mvn clean compile
mvn spring-boot:run
```

### 2.3 验证

```bash
# 健康检查
curl http://localhost:8080/api/reports

# Swagger 文档
浏览器打开 http://localhost:8080/doc.html
```

---

## 三、Python Agent 服务启动

### 3.1 创建虚拟环境

```bash
cd case-assistant-agent

# 创建并激活虚拟环境
python -m venv .venv
# Windows:
.venv\Scripts\activate
# Linux/Mac:
source .venv/bin/activate

# 安装依赖
pip install -r requirements.txt
```

### 3.2 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`：

```env
# LLM 配置（必填）
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o

# Embedding 配置（RAG 需要）
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIM=1536

# PostgreSQL（RAG 向量数据库）
PG_HOST=localhost
PG_PORT=5432
PG_DATABASE=case_assistant_db
PG_USER=postgres
PG_PASSWORD=postgres

# Java 后端地址（类案查询需要）
JAVA_BACKEND_URL=http://localhost:8080

# Dify（Agent 直接调 Dify 时需要）
DIFY_BASE_URL=http://localhost/v1
DIFY_WORKFLOW_KEY=app-xxx

# 法律文档目录（RAG 知识库源文件）
LEGAL_DOCS_DIR=./docs/legal
```

### 3.3 启动服务

```bash
uvicorn app.main:app --reload --port 8000
```

### 3.4 验证

```bash
# 健康检查
curl http://localhost:8000/health
# 返回: {"status":"ok","llm_model":"gpt-4o","embedding_model":"text-embedding-3-small"}

# Swagger 文档
浏览器打开 http://localhost:8000/docs
```

---

## 四、RAG 法律知识库构建

### 4.1 准备 PostgreSQL

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 4.2 准备法律文档

将文档放入 `case-assistant-agent/docs/legal/`：

```
docs/legal/
├── 刑法条文.pdf
├── 刑事诉讼法.docx
├── 证据规则.txt
├── 指导案例/
│   ├── 案例1.pdf
│   └── 案例2.pdf
```

支持格式：PDF、DOCX、TXT、MD

### 4.3 构建索引

```bash
curl -X POST http://localhost:8000/api/agent/rag/build-index \
  -H "Content-Type: application/json" \
  -d '{"doc_dir": "./docs/legal"}'

# 返回: {"status":"success","indexed_chunks": 1234}
```

### 4.4 测试检索

```bash
curl -X POST http://localhost:8000/api/agent/rag/search \
  -H "Content-Type: application/json" \
  -d '{"query": "盗窃罪量刑标准"}'
```

---

## 五、端到端验证（完整流程）

### 5.1 方式一：完整流程（Dify 初步分析 → Agent 深度分析）

这是系统的核心流程，模拟真实使用场景。

**第一步：上传文件并获取 Dify 初步分析结果**

```bash
# 上传文件到 Java 后端
curl -X POST http://localhost:8080/api/analysis/start \
  -F "files=@案件文书.pdf"

# 返回: {"code":202,"data":{"analysisId":"analysis-xxx"}}
```

**第二步：将 Dify 结果传给 Agent 做深度分析**

```bash
curl -X POST http://localhost:8080/api/agent/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "message": "请对这个案件进行深度质量分析，检索相关法条并查询类案",
    "difyResult": {
      "case_name": "张某盗窃案",
      "undertaker": "李检察官",
      "issues": [
        {"issue_category": "证据采信", "issue_type": "瑕疵"},
        {"issue_category": "程序规范", "issue_type": "错误"}
      ],
      "case_summary": "2024年3月，被告人张某...",
      "issueAnalysisContent": "1. 物证提取笔录缺少见证人签字...",
      "problem_description": "物证提取程序不规范",
      "supervision_point": "证据合法性"
    }
  }'
```

**第三步：查看 Agent 推理链**

```bash
# 用返回的 trace_id 查看详细推理过程
curl http://localhost:8000/api/agent/traces/{trace_id}
```

### 5.2 方式二：Agent 对话（问答模式）

```bash
curl -X POST http://localhost:8000/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "盗窃罪的量刑标准是什么？什么情况下可以从轻处罚？"}'
```

### 5.3 方式三：Agent 从头分析（跳过 Dify）

```bash
curl -X POST http://localhost:8000/api/agent/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "file_ids": ["dify-file-id-xxx"],
    "message": "请分析这个法律文书"
  }'
```

### 5.4 健康检查

```bash
# 检查 Agent 服务是否可用
curl http://localhost:8080/api/agent/health
# 返回: {"code":200,"data":{"agent_service_available":true,"status":"ok"}}
```

### 5.5 类案查询（独立接口）

```bash
# 查询证据采信类瑕疵案件
curl "http://localhost:8080/api/cases/search?issueCategory=证据采信&issueType=瑕疵&limit=5"
```

---

## 六、API 接口汇总

### Java 后端 (8080)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/analysis/start` | 上传文件，触发 Dify 初步分析 |
| POST | `/api/agent/analyze` | Agent 深度分析（传入 Dify 结果） |
| POST | `/api/agent/chat` | Agent 对话 |
| GET | `/api/agent/health` | Agent 服务健康检查 |
| GET | `/api/cases/search` | 类案查询 |
| GET | `/api/reports` | 报告列表 |
| GET | `/api/prosecutors/summary` | 办案人列表 |
| GET | `/api/cases/{caseId}` | 案件详情 |

### Python Agent 服务 (8000)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/analyze` | Agent 深度分析 |
| POST | `/api/agent/chat` | Agent 对话 |
| GET | `/api/agent/traces` | 推理链列表 |
| GET | `/api/agent/traces/{id}` | 推理链详情 |
| POST | `/api/agent/rag/build-index` | 构建 RAG 知识库 |
| POST | `/api/agent/rag/search` | 法律知识检索 |
| GET | `/health` | 健康检查 |

---

## 七、Docker 部署（可选）

```bash
cd case-assistant-agent
docker build -t case-assistant-agent .
docker run -d --name agent-service -p 8000:8000 --env-file .env case-assistant-agent
```

---

## 八、常见问题

### Q1: Agent 返回 503

Agent 服务未启动：
```bash
curl http://localhost:8000/health
# 如果不通，启动: uvicorn app.main:app --port 8000
```

### Q2: RAG 检索报错 "connection refused"

PostgreSQL 未启动或 pgvector 未安装：
```bash
pg_isready
# Ubuntu 安装 pgvector:
sudo apt install postgresql-15-pgvector
```

### Q3: Agent 分析没有查类案

检查 MySQL 中 `cases` 表是否有数据。`search_similar_cases` 依赖历史案件数据：
```bash
curl "http://localhost:8080/api/cases/search?issueCategory=证据采信"
# 如果返回空，说明还没有历史案件数据
```

### Q4: LLM 调用超时

检查 `.env` 中 `OPENAI_BASE_URL` 和 `OPENAI_API_KEY`。如果用代理：
```env
OPENAI_BASE_URL=https://your-proxy.com/v1
```

### Q5: Agent 不调用工具，直接给答案

Dify 结果中 `issues` 字段为空或问题描述不够具体。确保传入的 `difyResult` 包含有效的问题信息。

### Q6: LangGraph 报错 KeyError

确认 `app/agent/state.py` 包含所有字段：
```python
class AgentState(TypedDict):
    messages: ...
    file_ids: ...
    analysis_result: ...
    step_count: int
    trace_id: str
    total_tokens: int
    step_timings: list[dict]
```
