package com.hubu.caseassistant.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)   // 不序列化 null 字段，避免 Python Pydantic 对 list 字段传 null 报 422
public class AgentAnalyzeRequest {
    @JsonProperty("file_ids")
    private List<String> fileIds;
    private String message;

    /** Dify 工作流的分析结果，Agent 基于此做深度分析 */
    @JsonProperty("dify_result")
    private Map<String, Object> difyResult;

    /** 关联的案件 ID，回传给 Python 侧用于回写 CaseIssue */
    @JsonProperty("case_id")
    private String caseId;

    /**
     * 评查模板 checklist（方案 §6.3：Scenario 接 Agent 模式）。
     * 每项 {"code","name","category"}。为空时 Python 侧用默认四项 checklist。
     */
    private List<Map<String, Object>> checklist;

    public AgentAnalyzeRequest() {}

    public AgentAnalyzeRequest(List<String> fileIds, String message) {
        this.fileIds = fileIds;
        this.message = message;
    }

    public AgentAnalyzeRequest(String message, Map<String, Object> difyResult) {
        this.message = message;
        this.difyResult = difyResult;
    }
}
