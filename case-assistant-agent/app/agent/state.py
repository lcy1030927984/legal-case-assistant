from typing import Annotated, TypedDict
from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages


class AgentState(TypedDict):
    """LangGraph Agent 状态定义。

    评查员改造（见 docs/agent-redesign.md §4）：Agent 不再只产出散文 answer，
    而是按评查模板逐项判定，最终输出结构化 issues 列表回写 CaseIssue。
    """
    messages: Annotated[list[BaseMessage], add_messages]
    file_ids: list[str]
    analysis_result: dict | None
    step_count: int
    trace_id: str
    total_tokens: int
    step_timings: list[dict]

    # --- 评查员新增字段 ---
    # 评查模板项（checklist），由调用方注入或 Agent 自行加载
    checklist: list[dict]
    # 已完成评查的模板项编码集合，用于目标驱动退出
    evaluated_items: list[str]
    # 结构化问题清单（最终产出的主产物）
    issues: list[dict]
