from langchain_core.tools import BaseTool
import logging

logger = logging.getLogger(__name__)

# 工具注册表
_tool_registry: dict[str, BaseTool] = {}


def register_tool(tool: BaseTool):
    """注册一个工具到全局注册表"""
    _tool_registry[tool.name] = tool


def get_all_tools() -> list[BaseTool]:
    """获取所有已注册的工具列表"""
    _ensure_tools_loaded()
    return list(_tool_registry.values())


def get_tool_by_name(name: str) -> BaseTool | None:
    """根据名称获取工具"""
    _ensure_tools_loaded()
    return _tool_registry.get(name)


_tools_loaded = False


def _ensure_tools_loaded():
    """延迟加载工具，避免循环导入"""
    global _tools_loaded
    if _tools_loaded:
        return
    _tools_loaded = True

    from app.tools.dify_tool import trigger_dify_workflow, is_dify_available
    from app.tools.case_query_tool import query_case_info, query_prosecutor_info, search_similar_cases
    from app.tools.stats_tool import query_stats

    # Dify 工具按需注册：未配置时跳过，让 LLM 看不到该工具，避免 Agent 尝试调用未就绪服务
    if is_dify_available():
        register_tool(trigger_dify_workflow)
        logger.info("trigger_dify_workflow 工具已注册（Dify 配置就绪）")
    else:
        logger.warning("Dify 未配置（DIFY_WORKFLOW_KEY 为空或占位符），trigger_dify_workflow 未注册")

    register_tool(query_case_info)
    register_tool(query_prosecutor_info)
    register_tool(search_similar_cases)
    register_tool(query_stats)

    # RAG 工具可能未就绪（向量库未初始化），延迟注册
    try:
        from app.tools.legal_search_tool import legal_doc_search
        register_tool(legal_doc_search)
        logger.info("legal_doc_search 工具已注册（RAG 就绪）")
    except Exception as e:
        logger.warning("legal_doc_search 注册失败，RAG 未就绪: %s", e)
