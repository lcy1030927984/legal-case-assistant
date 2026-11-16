from llama_index.core import VectorStoreIndex, StorageContext
from llama_index.core.schema import BaseNode, Document
from llama_index.vector_stores.postgres import PGVectorStore
from llama_index.embeddings.openai import OpenAIEmbedding

from app.config import get_settings


_vector_store: PGVectorStore | None = None
_index: VectorStoreIndex | None = None


def get_embed_model() -> OpenAIEmbedding:
    settings = get_settings()
    return OpenAIEmbedding(
        model_name=settings.embedding_model,
        api_key=settings.openai_api_key,
        api_base=settings.openai_base_url,
        dimensions=settings.embedding_dim,
    )


def get_vector_store() -> PGVectorStore:
    global _vector_store
    if _vector_store is None:
        settings = get_settings()
        _vector_store = PGVectorStore.from_params(
            host=settings.pg_host,
            port=settings.pg_port,
            database=settings.pg_database,
            user=settings.pg_user,
            password=settings.pg_password,
            table_name="legal_documents",
            embed_dim=settings.embedding_dim,
        )
    return _vector_store


def get_index() -> VectorStoreIndex:
    global _index
    if _index is None:
        store = get_vector_store()
        embed_model = get_embed_model()
        storage_context = StorageContext.from_defaults(vector_store=store)
        _index = VectorStoreIndex.from_vector_store(
            vector_store=store,
            embed_model=embed_model,
            storage_context=storage_context,
        )
    return _index


def build_index_from_documents(documents: list[Document]) -> VectorStoreIndex:
    """从文档构建向量索引（首次入库时调用）"""
    store = get_vector_store()
    embed_model = get_embed_model()
    storage_context = StorageContext.from_defaults(vector_store=store)

    index = VectorStoreIndex.from_documents(
        documents,
        storage_context=storage_context,
        embed_model=embed_model,
        show_progress=True,
    )
    return index


def add_nodes(nodes: list[BaseNode]):
    """向已有索引追加节点"""
    store = get_vector_store()
    store.add(nodes)
