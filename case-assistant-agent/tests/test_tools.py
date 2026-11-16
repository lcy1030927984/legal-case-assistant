"""工具单元测试：验证工具定义和参数 schema"""
import pytest


def test_dify_tool_definition():
    from app.tools.dify_tool import trigger_dify_workflow
    assert trigger_dify_workflow.name == "trigger_dify_workflow"
    assert "file_id" in trigger_dify_workflow.args_schema.model_json_schema()["properties"]


def test_case_query_tool_definition():
    from app.tools.case_query_tool import query_case_info
    assert query_case_info.name == "query_case_info"
    assert "case_id" in query_case_info.args_schema.model_json_schema()["properties"]


def test_prosecutor_tool_definition():
    from app.tools.case_query_tool import query_prosecutor_info
    assert query_prosecutor_info.name == "query_prosecutor_info"
    assert "prosecutor_id" in query_prosecutor_info.args_schema.model_json_schema()["properties"]


def test_legal_search_tool_definition():
    from app.tools.legal_search_tool import legal_doc_search
    assert legal_doc_search.name == "legal_doc_search"
    assert "query" in legal_doc_search.args_schema.model_json_schema()["properties"]
