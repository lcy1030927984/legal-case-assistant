package com.hubu.caseassistant.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Agent 单条结构化问题 —— 对应 {@code docs/agent-redesign.md} §6.1 评查员产出契约中 issues 数组的一项。
 *
 * <p>由 Python Agent 评查产出的 JSON 反序列化而来，{@code @JsonIgnoreProperties(ignoreUnknown=true)}
 * 保证 Agent 新增字段不破坏 Java 侧。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentIssueItem {

    /** 问题类别：evidence / fact / law / procedure */
    private String category;

    /** 问题类型：瑕疵 / 错误 / 存疑 */
    @JsonProperty("issue_type")
    private String issueType;

    /** 严重程度：高 / 中 / 低 */
    private String severity;

    /** 问题一句话标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 法条引用，如 ["《刑事诉讼法》第55条"]。过渡期来自评查模板，RAG 上线后来自检索 */
    private List<String> citations;

    /** 参考类案 ID 列表 */
    @JsonProperty("similar_case_ids")
    private List<String> similarCaseIds;

    /** 该问题对应的推理步骤编号，指向 Trace */
    @JsonProperty("trace_step_ids")
    private List<String> traceStepIds;
}
