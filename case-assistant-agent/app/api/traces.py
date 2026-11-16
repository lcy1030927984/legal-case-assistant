from fastapi import APIRouter, HTTPException

from app.models.trace import AgentTrace
from app.trace.store import get_trace_store

router = APIRouter()


@router.get("/traces", response_model=list[AgentTrace])
async def list_traces(page: int = 1, size: int = 20):
    """查询 Agent 推理链列表，按时间倒序"""
    store = get_trace_store()
    return store.list_traces(page=page, size=size)


@router.get("/traces/{trace_id}", response_model=AgentTrace)
async def get_trace(trace_id: str):
    """查询单条推理链详情，包含完整的 thought → action → observation 链"""
    store = get_trace_store()
    trace = store.get(trace_id)
    if trace is None:
        raise HTTPException(status_code=404, detail=f"Trace {trace_id} 不存在")
    return trace
