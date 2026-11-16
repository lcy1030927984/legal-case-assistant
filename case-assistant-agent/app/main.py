import logging

from fastapi import FastAPI

from app.api import analyze, chat, traces, rag
from app.config import get_settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)

app = FastAPI(
    title="Case Assistant Agent Service",
    description="法律案件智能分析 Agent 服务，基于 LangGraph + RAG",
    version="1.0.0",
)

app.include_router(analyze.router, prefix="/api/agent", tags=["Agent 分析"])
app.include_router(chat.router, prefix="/api/agent", tags=["Agent 对话"])
app.include_router(traces.router, prefix="/api/agent", tags=["推理链追踪"])
app.include_router(rag.router, prefix="/api/agent", tags=["RAG 知识库"])


@app.get("/health")
async def health():
    settings = get_settings()
    return {
        "status": "ok",
        "llm_model": settings.llm_model,
        "embedding_model": settings.embedding_model,
    }
