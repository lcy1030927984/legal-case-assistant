import httpx
from langchain_core.tools import tool

from app.config import get_settings


@tool
def query_case_info(case_id: str) -> str:
    """根据案件 ID 查询案件的详细信息。

    从数据库中检索案件的完整信息，包括案件名称、办案人、
    问题分类、处理结果等。

    Args:
        case_id: 案件的唯一标识 ID
    """
    settings = get_settings()

    response = httpx.get(
        f"{settings.java_backend_url}/api/cases/{case_id}",
        timeout=10.0,
    )
    response.raise_for_status()

    data = response.json()
    return str(data.get("data", data))


@tool
def query_prosecutor_info(prosecutor_id: str) -> str:
    """查询办案人的画像信息，包括案件统计、问题分布等。

    Args:
        prosecutor_id: 办案人的唯一标识 ID
    """
    settings = get_settings()

    response = httpx.get(
        f"{settings.java_backend_url}/api/prosecutors/{prosecutor_id}/profile",
        timeout=10.0,
    )
    response.raise_for_status()

    data = response.json()
    return str(data.get("data", data))


@tool
def search_similar_cases(issue_category: str, issue_type: str = "") -> str:
    """根据问题类别和类型查询历史类似案件，用于类案对比分析。

    在数据库中检索具有相同问题类别和类型的已分析案件，
    了解历史上类似问题的处理方式和结果。

    Args:
        issue_category: 问题类别，如"证据采信"、"事实认定"、"法律适用"、"程序规范"
        issue_type: 问题类型，如"瑕疵"、"错误"（可选，不填则查该类别下所有）
    """
    settings = get_settings()

    params = {"issueCategory": issue_category, "limit": 10}
    if issue_type:
        params["issueType"] = issue_type

    response = httpx.get(
        f"{settings.java_backend_url}/api/cases/search",
        params=params,
        timeout=10.0,
    )
    response.raise_for_status()

    data = response.json()
    cases = data.get("data", [])

    if not cases:
        return f"未找到问题类别为 '{issue_category}' 的历史类似案件"

    results = []
    for i, case in enumerate(cases, 1):
        case_name = case.get("caseName", "未知")
        handler = case.get("handlerName", "未知")
        category = case.get("issueCategory", "")
        type_ = case.get("issueType", "")
        summary = case.get("caseSummary", "无摘要")
        conclusion = case.get("conclusionSituation", "未知")
        problem = case.get("problemDescription", "")

        result = (
            f"[{i}] {case_name}\n"
            f"  承办人: {handler}\n"
            f"  问题: {category} - {type_}\n"
            f"  案情: {summary[:200]}{'...' if len(summary) > 200 else ''}\n"
            f"  处理结果: {conclusion}"
        )
        if problem:
            result += f"\n  问题描述: {problem[:200]}{'...' if len(problem) > 200 else ''}"
        results.append(result)

    return "\n\n".join(results)
