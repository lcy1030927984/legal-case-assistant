from pydantic import BaseModel


class AnalyzeRequest(BaseModel):
    file_ids: list[str] = []
    message: str = "请分析这些法律文书，提取案件关键信息并评估质量"
    dify_result: dict | None = None
    # 评查员模式：关联的案件 ID，回传给 Java 侧用于回写 CaseIssue
    case_id: str | None = None
    # 评查模板 checklist（可选），由 Java 侧注入；为空时 Agent 用默认评查项。
    # 用 | None 兘容 Java 侧序列化 null 的情况（Jackson 默认会输出 "checklist":null，
    # 此时 list[dict]=[] 会校验失败 422）
    checklist: list[dict] | None = None


class ChatRequest(BaseModel):
    message: str
    conversation_id: str | None = None


class AgentStepResponse(BaseModel):
    step_number: int
    thought: str
    tool_name: str | None = None
    tool_input: dict | None = None
    tool_output: str | None = None
    duration_ms: int = 0


class AgentResponse(BaseModel):
    answer: str
    steps: list[AgentStepResponse]
    total_tokens: int = 0
    total_duration_ms: int = 0
    trace_id: str
    # 评查员结构化产出（见 docs/agent-redesign.md §6.1）。过渡期可能为空
    case_id: str | None = None
    summary: str | None = None
    issues: list[dict] = []
