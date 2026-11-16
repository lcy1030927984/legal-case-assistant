import logging

from app.rag.loader import load_legal_documents
from app.rag.splitter import split_documents
from app.rag.vector_store import build_index_from_documents

logger = logging.getLogger(__name__)


def build_knowledge_base(doc_dir: str) -> int:
    """构建法律知识库的完整流程

    1. 加载文档目录下的所有文件
    2. 切分为小块
    3. Embedding 后存入 PGVector

    Returns:
        处理的文档块数量
    """
    logger.info("开始加载文档目录: %s", doc_dir)
    documents = load_legal_documents(doc_dir)
    logger.info("加载完成，共 %d 个文档", len(documents))

    if not documents:
        logger.warning("目录下没有找到支持的文档文件")
        return 0

    logger.info("开始切分文档...")
    nodes = split_documents(documents)
    logger.info("切分完成，共 %d 个文本块", len(nodes))

    logger.info("开始构建向量索引...")
    build_index_from_documents(documents)
    logger.info("向量索引构建完成")

    return len(nodes)
