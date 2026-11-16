import json
import time
import uuid
import logging

from fastapi import APIRouter
from langchain_core.messages import HumanMessage

from app.agent.graph import get_agent_graph
from app.agent.state import AgentState
from app.agent.utils import extract_agent_steps
from app.models.request import AnalyzeRequest, AgentResponse
from app.models.trace import AgentTrace
from app.trace.store import get_trace_store

logger = logging.getLogger(__name__)
router = APIRouter()


def _build_user_message(request: AnalyzeRequest) -> str:
    """根据请求构造 Agent 的输入消息

    如果有 difyResult，将 Dify 分析结果格式化为上下文，
    让 Agent 基于此做深度分析。
    """
    if request.dify_result:
        dify_json = json.dumps(request.dify_result, ensure_ascii=False, indent=2)
        return (
            f"{request.message}\n\n"
            f"以下是 Dify 工作流的初步分析结果，请基于此进行深度分析：\n\n"
            f"```json\n{dify_json}\n```"
        )

    if request.file_ids:
        file_info = f"文件ID列表: {request.file_ids}"
        return f"{request.message}\n\n{file_info}"

    return request.message


@router.post("/analyze", response_model=AgentResponse)
async def analyze(request: AnalyzeRequest):
    """调用 Agent 进行案件深度分析

    支持两种模式：
    1. 传入 difyResult：Agent 基于 Dify 初步结果做深度分析（查法条、查类案）
    2. 传入 fileIds：Agent 从头开始分析（调 Dify workflow）
    """
    trace_id = str(uuid.uuid4())
    start_time = time.time()

    user_message = _build_user_message(request)

    initial_state: AgentState = {
        "messages": [HumanMessage(content=user_message)],
        "file_ids": request.file_ids,
        "analysis_result": None,
        "step_count": 0,
        "trace_id": trace_id,
        "total_tokens": 0,
        "step_timings": [],
        # 评查员模式新增；checklist 为 None 时回退空列表，走默认四项评查
        "checklist": request.checklist or [],
        "evaluated_items": [],
        "issues": [],
    }

    graph = get_agent_graph()
    final_state = graph.invoke(initial_state)

    total_duration = int((time.time() - start_time) * 1000)

    result = final_state.get("analysis_result", {})
    answer = result.get("answer", "分析未产生结果") if isinstance(result, dict) else str(result)
    summary = result.get("summary") if isinstance(result, dict) else None
    issues = result.get("issues", []) if isinstance(result, dict) else []

    step_timings = final_state.get("step_timings", [])
    step_responses, step_records = extract_agent_steps(
        final_state.get("messages", []),
        step_timings=step_timings,
    )

    total_tokens = final_state.get("total_tokens", 0)

    trace = AgentTrace(
        trace_id=trace_id,
        user_message=user_message[:500],
        steps=step_records,
        final_answer=answer,
        total_tokens=total_tokens,
        total_duration_ms=total_duration,
    )
    get_trace_store().save(trace)
    logger.info(
        "Agent 分析完成, trace_id=%s, 耗时=%dms, tokens=%d, 步骤数=%d",
        trace_id, total_duration, total_tokens, len(step_responses),
    )

    return AgentResponse(
        answer=answer,
        steps=step_responses,
        total_tokens=total_tokens,
        total_duration_ms=total_duration,
        trace_id=trace_id,
        case_id=request.case_id,
        summary=summary,
        issues=issues,
    )
