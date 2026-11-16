package com.hubu.caseassistant.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Agent 评查产出契约 —— 对应 {@code docs/agent-redesign.md} §6.1。
 *
 * <p>过渡期(P0) Python Agent 仍输出散文 {@code answer} + steps；P1 改造后输出结构化
 * {@code issues}。本 DTO 同时承载两种形态：Java 侧优先消费 {@code issues}，为空时回退到
 * {@code answer}，保证 Agent 改造前后 Java 侧都能工作。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentEvaluationResult {

    /** 散文形式的最终回答（过渡期主产物，P1 后退为兼容字段） */
    private String answer;

    /** 结构化问题清单（P1 后的主产物，过渡期为空） */
    private List<AgentIssueItem> issues;

    /** 本次评查涉及的案件 ID（Agent 回填，Java 侧据此写 CaseIssue.caseId） */
    @JsonProperty("case_id")
    private String caseId;

    /** 整体评查摘要 */
    private String summary;

    /** 推理步骤（与现有 AgentAnalyzeResponse.steps 同构，保留可观测） */
    private List<AgentStepDto> steps;

    @JsonProperty("total_tokens")
    private int totalTokens;

    @JsonProperty("total_duration_ms")
    private int totalDurationMs;

    @JsonProperty("trace_id")
    private String traceId;
}
