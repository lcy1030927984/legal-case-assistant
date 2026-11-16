import httpx
from langchain_core.tools import tool

from app.config import get_settings


_DIFY_KEY_PLACEHOLDERS = {"", "app-xxx"}


def is_dify_available() -> bool:
    """检查 Dify 是否已配置可用。

    判定规则：DIFY_WORKFLOW_KEY 非空且非占位符 app-xxx。
    用于工具注册和运行时降级，实现 Agent 与 Dify 解耦：
    未配置时跳过工具注册或返回友好提示，避免 httpx 抛异常中断 Agent 推理。
    """
    settings = get_settings()
    key = (settings.dify_workflow_key or "").strip()
    return key not in _DIFY_KEY_PLACEHOLDERS


@tool
def trigger_dify_workflow(file_id: str) -> str:
    """调用 Dify AI 工作流分析法律文书文件。

    上传文件后，使用此工具触发 Dify 平台的案件分析工作流，
    返回包含案件信息、问题分析等结构化结果。

    Args:
        file_id: Dify 平台返回的文件 ID
    """
    settings = get_settings()

    if not is_dify_available():
        return (
            "Dify 服务未配置（DIFY_WORKFLOW_KEY 为空或占位符 app-xxx），"
            "无法调用工作流。请基于已有上下文继续分析，"
            "或提示用户先配置 Dify 后再传入 file_ids。"
        )

    try:
        response = httpx.post(
            f"{settings.dify_base_url}/workflows/run",
            headers={
                "Authorization": f"Bearer {settings.dify_workflow_key}",
                "Content-Type": "application/json",
            },
            json={
                "inputs": {"file_id": file_id},
                "response_mode": "blocking",
                "user": "agent-service",
            },
            timeout=120.0,
        )
        response.raise_for_status()

        data = response.json()
        outputs = data.get("data", {}).get("outputs", {})
        return str(outputs)
    except (httpx.RequestError, httpx.HTTPStatusError) as e:
        return (
            f"Dify 工作流调用失败：{type(e).__name__}: {e}。"
            "Dify 服务可能未启动或不可达，请基于已有上下文继续分析。"
        )
