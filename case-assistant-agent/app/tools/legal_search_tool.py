from langchain_core.tools import tool


@tool
def legal_doc_search(query: str) -> str:
    """检索法律文书、法条和相关案例，用于辅助案件分析。

    在法律知识库中搜索与查询相关的法条、司法解释、指导案例等，
    返回最相关的 5 个文档片段。

    Args:
        query: 检索关键词或问题描述，如"盗窃罪量刑标准"
    """
    # 延迟导入，避免模块加载时就连接 PGVector
    from app.rag.vector_store import get_index

    index = get_index()
    retriever = index.as_retriever(similarity_top_k=5)
    nodes = retriever.retrieve(query)

    if not nodes:
        return "未找到相关法律文档"

    results = []
    for i, node in enumerate(nodes, 1):
        score = node.score if node.score else 0
        content = node.node.get_content()
        source = node.node.metadata.get("file_path", "未知来源")
        results.append(f"[{i}] (相关度: {score:.2f}) 来源: {source}\n{content}")

    return "\n\n---\n\n".join(results)
