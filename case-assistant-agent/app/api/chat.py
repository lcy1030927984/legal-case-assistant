import time
import uuid
import logging

from fastapi import APIRouter
from langchain_core.messages import HumanMessage

from app.agent.graph import get_agent_graph
from app.agent.state import AgentState
from app.agent.utils import extract_agent_steps
from app.models.request import ChatRequest, AgentResponse
from app.models.trace import AgentTrace
from app.trace.store import get_trace_store

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/chat", response_model=AgentResponse)
async def chat(request: ChatRequest):
    """与 Agent 对话，支持多轮问答

    Agent 可以使用工具查询案件信息、检索法律条文等，
    通过推理链给出专业回答。
    """
    trace_id = str(uuid.uuid4())
    start_time = time.time()

    initial_state: AgentState = {
        "messages": [HumanMessage(content=request.message)],
        "file_ids": [],
        "analysis_result": None,
        "step_count": 0,
        "trace_id": trace_id,
        "total_tokens": 0,
        "step_timings": [],
    }

    graph = get_agent_graph()
    final_state = graph.invoke(initial_state)

    total_duration = int((time.time() - start_time) * 1000)

    result = final_state.get("analysis_result", {})
    answer = result.get("answer", "未能生成回答") if isinstance(result, dict) else str(result)

    step_timings = final_state.get("step_timings", [])
    step_responses, step_records = extract_agent_steps(
        final_state.get("messages", []),
        step_timings=step_timings,
    )

    total_tokens = final_state.get("total_tokens", 0)

    trace = AgentTrace(
        trace_id=trace_id,
        user_message=request.message,
        steps=step_records,
        final_answer=answer,
        total_tokens=total_tokens,
        total_duration_ms=total_duration,
    )
    get_trace_store().save(trace)

    return AgentResponse(
        answer=answer,
        steps=step_responses,
        total_tokens=total_tokens,
        total_duration_ms=total_duration,
        trace_id=trace_id,
    )
