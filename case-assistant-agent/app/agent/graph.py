from langgraph.graph import StateGraph, END

from app.agent.state import AgentState
from app.agent.nodes import reason_node, act_node, answer_node
from app.config import get_settings


def _should_continue(state: AgentState) -> str:
    """判断是继续调用工具还是输出最终答案。

    改造后退出逻辑（方案 §7.1：目标驱动退出）：
      1. checklist 项全部 evaluated → 强制 answer（即使还想调工具也收尾）；
      2. 步数 ≥ max_steps → 强制 answer（兜底，防 LLM 不写标记死循环）；
      3. 最后一条消息有 tool_calls → act；
      4. 否则 → answer。
    """
    settings = get_settings()

    # 1. 目标驱动：checklist 全覆盖则收尾
    checklist = state.get("checklist", []) or []
    evaluated = set(state.get("evaluated_items", []))
    if checklist and evaluated:
        all_codes = {str(c.get("code", "")) for c in checklist if c.get("code")}
        if all_codes and all_codes.issubset(evaluated):
            return "answer"

    # 2. 步数兜底
    if state["step_count"] >= settings.agent_max_steps:
        return "answer"

    # 3. 有工具调用则执行
    last_message = state["messages"][-1]
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        return "act"

    return "answer"


def build_agent_graph():
    """构建 LangGraph Agent 状态图"""
    graph = StateGraph(AgentState)

    graph.add_node("reason", reason_node)
    graph.add_node("act", act_node)
    graph.add_node("answer", answer_node)

    graph.set_entry_point("reason")

    graph.add_conditional_edges("reason", _should_continue, {
        "act": "act",
        "answer": "answer",
    })
    graph.add_edge("act", "reason")
    graph.add_edge("answer", END)

    return graph.compile()


# 全局单例
_agent_graph = None


def get_agent_graph():
    global _agent_graph
    if _agent_graph is None:
        _agent_graph = build_agent_graph()
    return _agent_graph
