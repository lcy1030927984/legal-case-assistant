from pathlib import Path
from llama_index.core import SimpleDirectoryReader
from llama_index.core.schema import Document


def load_legal_documents(doc_dir: str) -> list[Document]:
    """加载法律文档目录下的所有支持格式文件

    支持格式: PDF, DOCX, TXT, MD
    """
    doc_path = Path(doc_dir)
    if not doc_path.exists():
        raise FileNotFoundError(f"文档目录不存在: {doc_dir}")

    reader = SimpleDirectoryReader(
        input_dir=str(doc_path),
        required_exts=[".pdf", ".docx", ".txt", ".md"],
        recursive=True,
    )
    documents = reader.load_data()

    # 为每个文档添加来源元数据
    for doc in documents:
        if "file_path" not in doc.metadata:
            doc.metadata["file_path"] = doc.metadata.get("source", "unknown")

    return documents
