from app.models.request import AgentStepResponse
from app.models.trace import AgentStepRecord


def extract_agent_steps(
    messages: list,
    step_timings: list[dict] | None = None,
) -> tuple[list[AgentStepResponse], list[AgentStepRecord]]:
    """从 Agent 消息列表中提取推理步骤

    遍历消息，将 AIMessage 的 tool_calls 和 ToolMessage 的结果
    配对成完整的推理步骤，并从 step_timings 中提取每步耗时。

    Args:
        messages: Agent 执行过程中的所有消息
        step_timings: 从 state 中获取的步骤计时信息

    Returns:
        (step_responses, step_records) - API 响应用和 Trace 存储用
    """
    step_responses: list[AgentStepResponse] = []
    pending_tool_calls: dict[str, AgentStepResponse] = {}

    for msg in messages:
        # AIMessage with tool_calls
        if hasattr(msg, "tool_calls") and msg.tool_calls:
            for tc in msg.tool_calls:
                step_resp = AgentStepResponse(
                    step_number=0,
                    thought=msg.content or "",
                    tool_name=tc["name"],
                    tool_input=tc["args"],
                )
                step_responses.append(step_resp)
                pending_tool_calls[tc["id"]] = step_resp

        # ToolMessage (result of a tool call)
        elif hasattr(msg, "tool_call_id") and msg.tool_call_id in pending_tool_calls:
            pending_tool_calls[msg.tool_call_id].tool_output = msg.content

    # 从 step_timings 中提取工具执行耗时
    if step_timings:
        tool_timing_iter = (
            t for t in step_timings if t.get("node") == "act"
        )
        for sr in step_responses:
            timing = next(tool_timing_iter, None)
            if timing:
                sr.duration_ms = timing.get("duration_ms", 0)

    # 重新编号
    for i, resp in enumerate(step_responses, 1):
        resp.step_number = i

    # 转为 Trace 记录
    step_records = [
        AgentStepRecord(
            step_number=sr.step_number,
            thought=sr.thought,
            tool_name=sr.tool_name,
            tool_input=sr.tool_input,
            tool_output=sr.tool_output,
            duration_ms=sr.duration_ms,
        )
        for sr in step_responses
    ]

    return step_responses, step_records
