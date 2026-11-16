# 智慧检察平台 - 开发进度文档

> 更新时间：2025-06-15
> 用途：换电脑工作时的上下文交接

---

## 一、项目概述

### 项目定位
面向检察机关的智能化办案平台。基于大模型构建 Agent 智能分析引擎，实现电子卷宗智能分析、瑕疵自动筛查、法律风险预警及检察官画像。系统通过多步推理自动关联法条依据和历史类案，解决人工阅卷效率低、标准不统一、缺乏法律知识支撑的痛点。

### 技术架构

```
┌─────────┐     ┌──────────────────────────────────────────────┐
│  前端    │────→│ Java 后端 (8080) - Spring Boot 3             │
└─────────┘     │  ├── 文件上传/下载 (WebFlux + SSE)           │
                │  ├── Dify 工作流调用                           │
                │  ├── 案件 CRUD / 检察官画像                    │
                │  └── 类案查询 API + Agent 调用入口             │
                └──────────────┬───────────────────────────────┘
                               │ HTTP
                ┌──────────────▼───────────────────────────────┐
                │ Python Agent 服务 (8000) - FastAPI            │
                │  ├── LangGraph Agent 引擎 (ReAct 循环)        │
                │  ├── 5 个 Tools                               │
                │  ├── RAG 法律知识库 (LlamaIndex + PGVector)   │
                │  └── Trace 推理链追踪                         │
                └──────────────────────────────────────────────┘
```

### 核心工作流

```
前端上传法律文书
    ↓
Java 后端：文件存储 → 上传 Dify → 触发 workflow → 获取初步分析结果
    ↓
Java 后端：将 Dify 结果转发给 Python Agent 服务
    ↓
Agent 多步推理：
  [reason] 理解 Dify 结果，识别具体问题
  [act]    legal_doc_search → 检索相关法条
  [act]    search_similar_cases → 查询历史类案
  [answer] 综合输出：问题 + 法条依据 + 类案参考 + 改进建议
    ↓
返回深度分析报告 + 推理链 Trace
```

---

## 二、已完成的工作

### 阶段一：Python Agent 服务骨架 ✅

**目录：** `case-assistant-agent/`

创建了完整的 Python 项目结构，包含：
- FastAPI 入口（`app/main.py`），7 个 API 路由
- 配置管理（`app/config.py`），基于 pydantic-settings + .env
- 数据模型（`app/models/request.py`、`app/models/trace.py`）
- Dockerfile 和 requirements.txt

### 阶段二：Agent 引擎（LangGraph）✅

**核心文件：**
- `app/agent/state.py` — AgentState TypedDict（7 个字段）
- `app/agent/graph.py` — LangGraph 状态图（reason → act → answer 循环）
- `app/agent/nodes.py` — 三个节点实现（含计时和 token 统计）
- `app/agent/prompts.py` — System prompt（引导四步分析流程）
- `app/agent/utils.py` — 推理步骤提取公共函数

**状态图结构：**
```
__start__ → reason → (条件判断) → act → reason (循环)
                        ↓
                     answer → __end__
```

**条件退出逻辑：**
- step_count >= 10 强制输出
- LLM 返回无 tool_calls 时输出最终答案

### 阶段三：RAG 法律知识库 ✅

**核心文件：**
- `app/rag/loader.py` — 文档加载（PDF/DOCX/TXT/MD）
- `app/rag/splitter.py` — 句子级文本切分（512 token 一块，50 token 重叠）
- `app/rag/vector_store.py` — PGVector 存储和检索
- `app/rag/indexer.py` — 知识库构建流程
- `app/tools/legal_search_tool.py` — RAG 检索 Tool

**技术选型：**
- 向量数据库：PGVector（PostgreSQL 扩展）
- Embedding：OpenAI text-embedding-3-small（1536 维）
- 框架：LlamaIndex

### 阶段四：Java 侧改造 ✅

**新增文件：**
- `config/AgentServiceProperties.java` — Agent 服务配置
- `dto/agent/AgentAnalyzeRequest.java` — 请求 DTO（含 difyResult 字段）
- `dto/agent/AgentAnalyzeResponse.java` — 响应 DTO
- `dto/agent/AgentStepDto.java` — 推理步骤 DTO
- `service/client/AgentServiceClient.java` — 调用 Python 服务的客户端

**修改文件：**
- `controller/CaseAnalysisController.java` — 新增 4 个端点：
  - POST `/api/agent/analyze` — Agent 深度分析
  - POST `/api/agent/chat` — Agent 对话
  - GET `/api/agent/health` — 健康检查
  - GET `/api/cases/search` — 类案查询
- `repository/CaseRepository.java` — 新增 2 个查询方法
- `resources/application.yml` — 新增 agent.service 配置

### 阶段五：Trace 可观测性 ✅

**实现内容：**
- `reason_node` 和 `act_node` 记录每步耗时
- `reason_node` 提取 LLM 的 `usage_metadata.total_tokens` 累加到 state
- `extract_agent_steps` 从 `step_timings` 中匹配工具执行耗时
- Trace 持久化为 JSON 文件（`app/trace/store.py`）
- API 查询：GET `/api/agent/traces` 和 GET `/api/agent/traces/{id}`

### 阶段六：问题驱动的多步深度分析 ✅

**核心改动：**
- 重写 `prompts.py`：引导 Agent 按"理解问题→查法条→查类案→综合输出"四步走
- 新增 `search_similar_cases` 工具：根据问题类别/类型查询历史类似案件
- 调整 `analyze.py`：支持传入 Dify 结果作为上下文
- Java 侧新增 `/api/cases/search` 端点供 Agent 调用

**Agent 现有 5 个工具：**
1. `trigger_dify_workflow` — 调 Dify 工作流
2. `query_case_info` — 查询案件详情
3. `query_prosecutor_info` — 查询办案人画像
4. `search_similar_cases` — 类案检索（调 Java 后端 DB）
5. `legal_doc_search` — RAG 法条检索（调 PGVector）

---

## 三、已解决的问题

### 1. 依赖版本冲突
- **问题：** llama-index-core 要求 pydantic<2.10.0，但最初指定了 2.10.3
- **解决：** requirements.txt 改为 `pydantic>=2.7.0,<2.10.0`，使用宽松版本约束
- **问题：** llama-index-vector-stores-pgvector 包名错误
- **解决：** 改为 `llama-index-vector-stores-postgres`

### 2. 模块加载时连接数据库
- **问题：** `legal_search_tool.py` 顶层 import `get_index()` 会在模块加载时触发 PG 连接
- **解决：** 将 `from app.rag.vector_store import get_index` 移入函数体内（延迟导入）

### 3. LangGraph API 变更
- **问题：** pip 安装的 langgraph 版本（1.2.5）比 requirements.txt 指定的（0.2.60）新很多
- **解决：** requirements.txt 改为宽松版本 `langgraph>=0.2.0`，实际安装的 1.2.5 API 兼容

### 4. Agent 工具执行耗时丢失
- **问题：** `act_node` 计算了 duration 但是局部变量，执行完就丢了
- **解决：** 在 AgentState 新增 `step_timings` 字段，将耗时信息存入 state

### 5. Agent 缺乏业务价值
- **问题：** 改造后的 Agent 只是换了调用方式，结果和直接调 Dify 一样
- **解决：** 实现"问题驱动的多步深度分析"——Agent 收到 Dify 初步结果后，自动检索法条和类案，输出带法律依据的深度报告

---

## 四、项目文件清单

### Java 侧（src/main/java/com/hubu/caseassistant/）

```
config/
  ├── DifyApiProperties.java          # Dify 配置
  ├── AgentServiceProperties.java     # [新增] Agent 服务配置
  └── FileStorageProperties.java      # 文件存储配置
controller/
  └── CaseAnalysisController.java     # [修改] 新增 Agent + 类案端点
dto/
  ├── agent/
  │   ├── AgentAnalyzeRequest.java    # [新增]
  │   ├── AgentAnalyzeResponse.java   # [新增]
  │   └── AgentStepDto.java           # [新增]
  ├── ApiResponse.java
  ├── DifyWorkflowOutput.java         # Dify 返回结构
  └── ...
model/
  ├── Case.java                       # 案件实体（70+ 字段）
  ├── ProsecutorProfile.java
  └── Report.java
repository/
  ├── CaseRepository.java             # [修改] 新增类案查询方法
  └── ReportRepository.java
service/
  ├── client/
  │   ├── DifyApiClient.java          # Dify 工作流客户端
  │   ├── DifyChatApiClient.java      # Dify 聊天客户端
  │   ├── AgentServiceClient.java     # [新增] Python Agent 客户端
  │   └── SupconFeignService.java
  └── impl/
      └── CaseAnalysisServiceImpl.java
resources/
  └── application.yml                 # [修改] 新增 agent.service 配置
```

### Python 侧（case-assistant-agent/）

```
app/
  ├── main.py                         # FastAPI 入口，7 个路由
  ├── config.py                       # 配置管理
  ├── agent/
  │   ├── state.py                    # AgentState（7 字段）
  │   ├── graph.py                    # LangGraph 状态图
  │   ├── nodes.py                    # reason/act/answer 节点
  │   ├── prompts.py                  # System prompt（四步分析流程）
  │   └── utils.py                    # 推理步骤提取
  ├── tools/
  │   ├── registry.py                 # 工具注册表
  │   ├── dify_tool.py                # trigger_dify_workflow
  │   ├── case_query_tool.py          # query_case_info + search_similar_cases
  │   └── legal_search_tool.py        # legal_doc_search（RAG）
  ├── rag/
  │   ├── loader.py                   # 文档加载
  │   ├── splitter.py                 # 文本切分
  │   ├── vector_store.py             # PGVector 操作
  │   └── indexer.py                  # 知识库构建
  ├── trace/
  │   └── store.py                    # Trace 持久化（JSON 文件）
  ├── models/
  │   ├── request.py                  # 请求/响应 DTO
  │   └── trace.py                    # Trace 数据模型
  └── api/
      ├── analyze.py                  # POST /api/agent/analyze
      ├── chat.py                     # POST /api/agent/chat
      ├── traces.py                   # GET /api/agent/traces
      └── rag.py                      # POST /api/agent/rag/*
tests/
  ├── test_basic.py                   # 8 个基础测试
  └── test_tools.py                   # 4 个工具定义测试
docs/legal/                           # 法律文档目录（待填充）
requirements.txt
Dockerfile
.env.example
README.md                             # 使用文档
```

---

## 五、下一步计划

### 优先级高

1. **填充法律知识库**
   - 准备法律文档：刑法、刑诉法、证据规则、指导案例
   - 放入 `docs/legal/` 目录
   - 调用 `POST /api/agent/rag/build-index` 构建索引
   - 测试检索效果

2. **端到端联调**
   - 启动 Java 后端 + Python Agent 服务 + Dify + MySQL + PostgreSQL
   - 上传真实案件文书，走完完整流程
   - 验证 Agent 是否正确调用 5 个工具

3. **优化 System Prompt**
   - 根据实际分析效果调整 prompts.py
   - 可能需要针对不同案件类型（盗窃、故意伤害等）定制 prompt

### 优先级中

4. **前端 Trace 查看页面**
   - 调用 GET /api/agent/traces 展示推理链列表
   - 点击展开详情，用颜色区分 thought/action/observation

5. **Agent 对话上下文保持**
   - 当前 /api/agent/chat 是无状态的
   - 需要支持 conversation_id 维持多轮对话

6. **错误处理增强**
   - Agent 工具调用失败时的重试机制
   - Dify 超时时的降级策略

### 优先级低

7. **性能优化**
   - RAG 检索结果缓存
   - Agent 并行调用多个工具（当前是串行）

8. **测试补充**
   - Agent 引擎的集成测试
   - RAG 检索准确率评估

---

## 六、核心设计思路

### 为什么用 LangGraph 而不是 LangChain Chain
- Chain 是线性的，不支持条件分支和循环
- Agent 需要"推理→执行→再推理"的循环，LangGraph 的状态图天然支持
- LangGraph 可以精确控制最大步数和退出条件

### 为什么 Java + Python 双服务
- Java 负责业务（CRUD、事务、文件管理），Spring Boot 生态成熟
- Python 负责 AI（LangGraph、LlamaIndex），生态丰富且面试加分
- 两个服务通过 HTTP 通信，可独立部署和扩缩容

### 为什么 Dify + Agent 两层
- Dify 擅长结构化信息提取（从文书中抽取案件字段）
- Agent 擅长多步推理和知识关联（问题→法条→类案→建议）
- 两层各司其职，比单层效果好

### RAG 的价值
- 单次 LLM 调用只能靠训练数据回答，法条可能过时或不准确
- RAG 从本地知识库检索，法条来源可控、可更新
- 检察官需要的是"引用具体法条"，不是"AI 觉得"

### Trace 的价值
- Agent 是黑盒，出了问题不知道哪步出错
- Trace 记录完整的 thought → action → observation 链
- 可以回放推理过程，定位是"理解错了"还是"检索错了"还是"工具出错了"

---

## 七、启动检查清单

换电脑后需要做的事：

- [ ] 安装 JDK 17+、Maven 3.8+、Python 3.11+
- [ ] 安装 MySQL 8.0+，创建数据库 `case_assistant_db2`
- [ ] 安装 PostgreSQL 15+，安装 pgvector 扩展，创建数据库 `case_assistant_db`
- [ ] 安装 LibreOffice 7.0+
- [ ] 准备 Dify 平台，获取 API Key
- [ ] 准备 OpenAI API Key（或兼容的代理）
- [ ] Java 后端：编辑 `application.yml`，配置数据库和 Dify
- [ ] Python 服务：复制 `.env.example` 为 `.env`，填入 API Key 和数据库配置
- [ ] Python 服务：`python -m venv .venv && pip install -r requirements.txt`
- [ ] 准备法律文档放入 `docs/legal/`，构建 RAG 索引
- [ ] 启动 Java：`mvn spring-boot:run`
- [ ] 启动 Python：`uvicorn app.main:app --port 8000`
- [ ] 验证：`curl http://localhost:8080/api/agent/health`

---

## 八、简历描述（定稿）

**项目描述：** 面向检察机关的智能化办案平台。基于大模型构建 Agent 智能分析引擎，实现电子卷宗智能分析、瑕疵自动筛查、法律风险预警及检察官画像。系统通过多步推理自动关联法条依据和历史类案，解决人工阅卷效率低、标准不统一、缺乏法律知识支撑的痛点。

**主要工作：**

1. 基于 Spring WebFlux 响应式框架与 SSE 协议，对接 Dify API 构建异步 AI 分析引擎，实现流式响应与首字低延迟输出。

2. 实现文件处理与存储优化：集成 LibreOffice 构建文档格式转换引擎，实现 .doc 到 .pdf 全自动转换；设计基于 MD5 的文件校验机制，实现秒传与存储去重。

3. 设计并实现 Agent 深度分析服务：基于 LangGraph 构建 ReAct Agent 引擎，接收 Dify 初步分析结果后，Agent 自主执行多步推理——识别具体问题、通过 RAG（LlamaIndex + PGVector）检索相关法条依据、查询历史类案进行对比分析，最终输出包含法条引用和类案参考的结构化质量评估报告，解决了单次 AI 分析无法关联法律知识和历史案例的问题。

4. 实现 Agent 全链路可观测：设计 Trace 机制，记录每步推理的 thought → action → observation 链及 token 消耗，支持推理过程回放与问题定位。

**核心技术栈：** Spring Boot 3, WebFlux, SSE, Python, FastAPI, LangGraph, LlamaIndex, PGVector, Dify, MySQL, Docker
