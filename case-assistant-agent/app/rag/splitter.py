from llama_index.core.node_parser import SentenceSplitter
from llama_index.core.schema import Document, BaseNode


def split_documents(
    documents: list[Document],
    chunk_size: int = 512,
    chunk_overlap: int = 50,
) -> list[BaseNode]:
    """将文档切分为适合 embedding 的小块

    使用句子级别的切分，保证语义完整性。

    Args:
        documents: 原始文档列表
        chunk_size: 每块的最大 token 数
        chunk_overlap: 块之间的重叠 token 数
    """
    splitter = SentenceSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
    )
    return splitter.get_nodes_from_documents(documents)
