import time
import json
import re
import logging

from langchain_core.messages import SystemMessage, AIMessage
from langchain_openai import ChatOpenAI

from app.config import get_settings
from app.agent.state import AgentState
from app.agent.prompts import SYSTEM_PROMPT, build_checklist_block
from app.tools.registry import get_all_tools

logger = logging.getLogger(__name__)

# 匹配 LLM 回复中的 [EVAL:code] 标记，用于推进 evaluated_items
_EVAL_TAG_RE = re.compile(r"\[EVAL:([^\]]+)\]")

# 格式兜底重试用的强提示：当 Agent 评查完后没按契约输出 JSON 时，用此 prompt
# 强制 LLM 把评查结论转成纯 JSON。不绑定工具，仅要求纯文本 JSON 输出。
_JSON_FORCE_PROMPT = """你是一个 JSON 转换器。下面会给你 Agent 的评查结论（可能是一段 Markdown 散文）。
你的任务：把它转成且只转成下面格式的 JSON，用 ```json 代码块包裹，代码块之外不要任何文字。

```json
{
  "summary": "整体评查摘要",
  "issues": [
    {
      "category": "evidence|fact|law|procedure",
      "issue_type": "瑕疵|错误|存疑",
      "severity": "高|中|低",
      "title": "问题标题",
      "description": "详细描述",
      "citations": ["法条条款"],
      "similar_case_ids": ["类案ID"],
      "trace_step_ids": []
    }
  ]
}
```

规则：
- 只输出一个 ```json 代码块，禁止任何 Markdown 报告、禁止任何代码块之外的文字。
- citations 必须来自评查结论里提到的法条（如"《刑法》第二百七十五条"），不要编造。
- 原结论里提到的每个问题都要转成一条 issue。原结论没提到的问题不要添加。
- 字符串值里不要出现未转义的双引号；如需引用，用书名号《》或单引号。
"""


def _get_llm() -> ChatOpenAI:
    settings = get_settings()
    return ChatOpenAI(
        model=settings.llm_model,
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url,
        temperature=0,
    )


def reason_node(state: AgentState) -> dict:
    """推理节点：LLM 分析当前状态，决定调用哪个工具或给出最终答案。

    改造：把 checklist 渲染进 system message 驱动逐项评查；从回复中解析
    [EVAL:code] 标记维护 evaluated_items，供 graph 目标驱动退出判定。
    """
    llm = _get_llm().bind_tools(get_all_tools())

    # 把 checklist 注入 system prompt（方案 §7.1：评查模板作为 checklist 驱动）
    checklist = state.get("checklist", [])
    system_content = SYSTEM_PROMPT + "\n\n" + build_checklist_block(checklist)

    # 若已标记完成的项，提示 LLM 还剩哪些，避免重复评查
    evaluated = set(state.get("evaluated_items", []))
    if evaluated:
        all_codes = {str(c.get("code", "")) for c in checklist} if checklist else set()
        remaining = all_codes - evaluated
        if remaining:
            system_content += f"\n\n已完成的评查项：{sorted(evaluated)}\n尚未完成：{sorted(remaining)}，请继续。"
        else:
            system_content += "\n\n所有评查项已完成，请输出最终 JSON 问题清单。"

    messages = [SystemMessage(content=system_content)] + state["messages"]

    start = time.time()
    response = llm.invoke(messages)
    duration_ms = int((time.time() - start) * 1000)

    # 诊断日志：LLM 第一次返回了什么，有没有 tool_calls
    has_tools = bool(getattr(response, "tool_calls", None))
    raw_content = getattr(response, "content", "") or ""
    logger.info(
        "traceId=%s reason_node step=%d has_tool_calls=%s content_preview=%.300s",
        state.get("trace_id"), state.get("step_count", 0), has_tools, raw_content
    )

    # 提取 token 使用量
    tokens_used = 0
    if hasattr(response, "usage_metadata") and response.usage_metadata:
        tokens_used = response.usage_metadata.get("total_tokens", 0)

    # 记录推理步骤耗时
    step_timings = list(state.get("step_timings", []))
    step_timings.append({
        "node": "reason",
        "duration_ms": duration_ms,
        "has_tool_calls": bool(getattr(response, "tool_calls", None)),
    })

    # 解析 [EVAL:code] 标记，累加到 evaluated_items（方案 §7.1：维护 evaluated_items）
    new_evaluated = list(state.get("evaluated_items", []))
    content = getattr(response, "content", "") or ""
    for m in _EVAL_TAG_RE.finditer(content):
        code = m.group(1).strip()
        if code and code not in new_evaluated:
            new_evaluated.append(code)
    if new_evaluated != list(state.get("evaluated_items", [])):
        logger.info("traceId=%s evaluated_items 更新为 %s",
                    state.get("trace_id"), new_evaluated)

    return {
        "messages": [response],
        "step_count": state["step_count"] + 1,
        "total_tokens": state.get("total_tokens", 0) + tokens_used,
        "step_timings": step_timings,
        "evaluated_items": new_evaluated,
    }


def act_node(state: AgentState) -> dict:
    """执行节点：执行 LLM 请求的工具调用，返回工具结果"""
    from app.tools.registry import get_tool_by_name

    last_message: AIMessage = state["messages"][-1]
    tool_results = []
    step_timings = list(state.get("step_timings", []))

    for tool_call in last_message.tool_calls:
        tool_name = tool_call["name"]
        tool_args = tool_call["args"]

        tool = get_tool_by_name(tool_name)
        if tool is None:
            result = f"错误：未找到工具 '{tool_name}'"
            duration = 0
        else:
            start = time.time()
            try:
                result = tool.invoke(tool_args)
            except Exception as e:
                result = f"工具执行出错: {e}"
            duration = int((time.time() - start) * 1000)

        step_timings.append({
            "node": "act",
            "tool_name": tool_name,
            "duration_ms": duration,
        })

        tool_results.append({
            "role": "tool",
            "content": str(result),
            "tool_call_id": tool_call["id"],
        })

    return {
        "messages": tool_results,
        "step_timings": step_timings,
    }


def answer_node(state: AgentState) -> dict:
    """最终答案节点：从 LLM 最后一条消息解析结构化评查产出。

    改造后（见 docs/agent-redesign.md §6.1）：从 LLM 输出中提取 JSON 代码块，
    解析为 {summary, issues}。若 LLM 没按契约输出 JSON（ReAct 后给散文总结的常见情况），
    用一条强提示再调一次 LLM 要求纯 JSON 兜底重试，仍失败则回退散文。
    """
    last_message = state["messages"][-1]
    raw = last_message.content if hasattr(last_message, "content") else str(last_message)

    summary, issues = _parse_structured_output(raw)

    # 兜底重试：第一次没解析到 issues，再调一次 LLM 强制要求 JSON
    if not issues:
        logger.info("answer_node 首次未解析到 JSON，触发格式兜底重试（traceId=%s）",
                    state.get("trace_id"))
        retry_raw = _force_json_output(state, raw)
        if retry_raw and retry_raw != raw:
            summary, issues = _parse_structured_output(retry_raw)
            if issues:
                raw = retry_raw
                logger.info("格式兜底重试成功，解析到 %d 条 issues（traceId=%s）",
                            len(issues), state.get("trace_id"))

    answer = raw
    if issues:
        answer = summary or "评查完成，详见 issues 结构化清单。"

    if not issues:
        logger.info("answer_node 未解析到结构化 issues，回退散文 answer（traceId=%s）",
                    state.get("trace_id"))

    return {
        "analysis_result": {
            "answer": answer,
            "summary": summary,
            "issues": issues,
        },
        "issues": issues,
    }


def _force_json_output(state: AgentState, prev_answer: str) -> str:
    """用强提示要求 LLM 把评查结论转为纯 JSON 输出。

    不绑定工具（answer 阶段不需要再调工具），temperature=0 提高格式遵守度。
    """
    llm = _get_llm()
    messages = [
        SystemMessage(content=_JSON_FORCE_PROMPT),
        SystemMessage(content=f"Agent 此前的评查结论如下，请据此输出 JSON：\n\n{prev_answer}"),
    ]
    try:
        resp = llm.invoke(messages)
        return resp.content if hasattr(resp, "content") else str(resp)
    except Exception as e:
        logger.warning("格式兜底重试调用 LLM 失败: %s", e)
        return ""


def _parse_structured_output(raw: str) -> tuple[str, list[dict]]:
    """从 LLM 输出中提取 JSON 代码块并解析为 (summary, issues)。

    容错策略：
    1. 优先找 ```json ... ``` 代码块；
    2. 找不到则尝试整段当 JSON 解析；
    3. 解析失败返回 ("", [])，由调用方回退散文。
    """
    if not raw:
        return "", []

    # 1. 提取 json 代码块（贪婪匹配整个代码块，避免非贪婪 .*? 在嵌套 JSON
    #    里遇到第一个 } 就截断导致抓不全）
    json_str = None
    m = re.search(r"```json\s*(\{.*\})\s*```", raw, re.DOTALL)
    if m:
        json_str = m.group(1)
    else:
        # 2. 尝试整段（贪婪匹配最外层 {...}）
        m2 = re.search(r"\{.*\}", raw, re.DOTALL)
        if m2:
            json_str = m2.group(0)

    if not json_str:
        return "", []

    data = None
    try:
        data = json.loads(json_str)
    except json.JSONDecodeError:
        # GLM 等模型在 JSON 字符串值里常写未转义的中文双引号导致解析失败，
        # 尝试用修复后的 JSON 再解析一次（见 _repair_json_quotes）
        try:
            repaired = _repair_json_quotes(json_str)
            data = json.loads(repaired)
            logger.info("JSON 修复转义后解析成功")
        except Exception as e2:
            logger.warning("结构化输出 JSON 解析失败（修复后仍失败）: %s", e2)
            return "", []

    summary = data.get("summary", "") if isinstance(data, dict) else ""
    issues_raw = data.get("issues", []) if isinstance(data, dict) else []
    issues = [i for i in issues_raw if isinstance(i, dict)] if isinstance(issues_raw, list) else []

    # 规范化每条 issue：保证字段存在，citations 必须是数组
    for issue in issues:
        issue.setdefault("category", None)
        issue.setdefault("issue_type", None)
        issue.setdefault("severity", None)
        issue.setdefault("title", None)
        issue.setdefault("description", None)
        if not isinstance(issue.get("citations"), list):
            issue["citations"] = []
        if not isinstance(issue.get("similar_case_ids"), list):
            issue["similar_case_ids"] = []
        if not isinstance(issue.get("trace_step_ids"), list):
            issue["trace_step_ids"] = []

    return summary, issues


def _repair_json_quotes(json_str: str) -> str:
    """修复 JSON 字符串值内未转义的双引号。

    状态机扫描：在字符串内部时，遇到 " 若其后不是结构性边界
    (", " : " ] " } 或行尾) 则视为字符串内部的引号，转义为 \\"。
    """
    out = []
    in_str = False
    i = 0
    n = len(json_str)
    while i < n:
        ch = json_str[i]
        if not in_str:
            out.append(ch)
            if ch == '"':
                in_str = True
            i += 1
            continue
        # 在字符串内部
        if ch == '\\':
            # 转义序列，原样保留两个字符
            out.append(ch)
            if i + 1 < n:
                out.append(json_str[i + 1])
                i += 2
            else:
                i += 1
            continue
        if ch == '"':
            # 判断这是字符串结束引号，还是内部未转义引号
            # 看后面（跳过空白）是否是结构性字符 , : ] }
            j = i + 1
            while j < n and json_str[j] in ' \t\r\n':
                j += 1
            if j >= n or json_str[j] in ',:}]':
                # 字符串结束
                out.append(ch)
                in_str = False
                i += 1
                continue
            else:
                # 字符串内部的引号，转义
                out.append('\\"')
                i += 1
                continue
        out.append(ch)
        i += 1
    return ''.join(out)
