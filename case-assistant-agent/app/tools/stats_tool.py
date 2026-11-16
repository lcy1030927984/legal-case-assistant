import httpx
from langchain_core.tools import tool

from app.config import get_settings


@tool
def query_stats(question: str) -> str:
    """自然语言查询案件统计数据。

    用于回答关于检察官办案数量、问题分布等统计类问题，例如
    "张三办了多少案"、"证据瑕疵最多的检察官是谁"。
    后端会把自然语言转成 SQL 查统计库返回结果。

    Args:
        question: 自然语言统计问题
    """
    settings = get_settings()

    response = httpx.post(
        f"{settings.java_backend_url}/api/data-query/ask",
        json={"question": question},
        timeout=30.0,
    )
    response.raise_for_status()

    data = response.json()
    # Java 侧 ApiResponse 结构：{code, message, data}
    payload = data.get("data", data)
    return str(payload)
