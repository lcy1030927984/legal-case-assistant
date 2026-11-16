import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.rag.indexer import build_knowledge_base
from app.tools.legal_search_tool import legal_doc_search

logger = logging.getLogger(__name__)
router = APIRouter()


class BuildIndexRequest(BaseModel):
    doc_dir: str = "./docs/legal"


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5


@router.post("/rag/build-index")
async def build_index(request: BuildIndexRequest):
    """构建法律知识库索引

    扫描指定目录下的法律文档，切分后 embedding 存入 PGVector。
    """
    try:
        count = build_knowledge_base(request.doc_dir)
        return {"status": "success", "indexed_chunks": count}
    except FileNotFoundError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error("构建索引失败: %s", e)
        raise HTTPException(status_code=500, detail=f"构建索引失败: {e}")


@router.post("/rag/search")
async def search_knowledge_base(request: SearchRequest):
    """在法律知识库中检索相关文档"""
    try:
        result = legal_doc_search.invoke({"query": request.query})
        return {"query": request.query, "results": result}
    except Exception as e:
        logger.error("检索失败: %s", e)
        raise HTTPException(status_code=500, detail=f"检索失败: {e}")
