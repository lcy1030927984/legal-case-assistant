"""基础验证测试：确认模块结构和核心类可以正常导入"""
import pytest


def test_config_loads():
    from app.config import Settings
    s = Settings(openai_api_key="test-key")
    assert s.llm_model == "gpt-4o"
    assert s.pg_connection_string.startswith("postgresql://")


def test_agent_state_structure():
    from app.agent.state import AgentState
    # 验证 TypedDict 包含所有必需字段
    fields = AgentState.__annotations__
    assert "messages" in fields
    assert "file_ids" in fields
    assert "analysis_result" in fields
    assert "step_count" in fields
    assert "trace_id" in fields


def test_request_models():
    from app.models.request import AnalyzeRequest, ChatRequest, AgentResponse
    req = AnalyzeRequest(file_ids=["f1", "f2"], message="测试")
    assert len(req.file_ids) == 2

    chat_req = ChatRequest(message="你好")
    assert chat_req.conversation_id is None


def test_trace_model():
    from app.models.trace import AgentTrace, AgentStepRecord
    trace = AgentTrace(user_message="测试消息")
    assert trace.trace_id is not None
    assert trace.steps == []

    step = AgentStepRecord(step_number=1, thought="思考", tool_name="test")
    assert step.tool_output is None


def test_trace_store_save_and_get():
    from app.trace.store import TraceStore
    from app.models.trace import AgentTrace, AgentStepRecord
    import tempfile, os

    with tempfile.TemporaryDirectory() as tmpdir:
        store = TraceStore(storage_dir=tmpdir)

        trace = AgentTrace(
            user_message="测试",
            steps=[AgentStepRecord(step_number=1, thought="思考")],
            final_answer="回答",
        )
        store.save(trace)

        retrieved = store.get(trace.trace_id)
        assert retrieved is not None
        assert retrieved.final_answer == "回答"
        assert len(retrieved.steps) == 1

        assert store.count() == 1
        traces = store.list_traces()
        assert len(traces) == 1


def test_extract_agent_steps_empty():
    from app.agent.utils import extract_agent_steps
    responses, records = extract_agent_steps([])
    assert responses == []
    assert records == []


def test_tool_registry_structure():
    from app.tools.registry import _tool_registry
    # 注册表初始为空（工具延迟加载）
    assert isinstance(_tool_registry, dict)


def test_prompts_exist():
    from app.agent.prompts import SYSTEM_PROMPT
    assert "法律" in SYSTEM_PROMPT
    assert "工具" in SYSTEM_PROMPT
