package com.hubu.caseassistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Dify 工作流输出的结构化 DTO，代替原始 JsonNode 解析。
 * 字段均有 @JsonProperty 映射，类型不匹配时 Jackson 在校验阶段就会报错。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifyWorkflowOutput {

    private String undertaker;

    @JsonProperty("case_name")
    private String caseName;

    private String organizer;

    private Integer tenure;

    /** 问题列表（数组形式） */
    private List<IssueItem> issues;

    // ---- 单问题回退字段 ----
    @JsonProperty("issue_category")
    private String issueCategory;

    @JsonProperty("issue_type")
    private String issueType;

    // ---- 案件详细信息 ----
    private String dossierFileNumber;
    private String caseStatus;
    private String caseSummary;

    @JsonProperty("departmentCaseNumber")
    private String departmentCaseNumber;

    private String transferUnit;
    private String transferReason;
    private String transferOtherReason;
    private String conclusionSituation;
    private String conclusionReason;
    private String conclusionOtherReason;
    private String conclusionAmount;
    private String isPleaBargain;
    private String prosecutionOpinionBookNumber;
    private String firstTrialResult;
    private String probationPeriod;
    private String firstTrialAnnouncementSentence;
    private String acceptanceDate;
    private String conclusionDate;
    private Integer processingDays;
    private String firstTrialAnnouncementDate;

    // ---- 大段文本内容 ----
    private String issueAnalysisContent;
    private String issueHandlingStatusContent;
    private String evidentiaryMaterialsContent;
    private String thinkingProcessReport;

    // ---- 扩展字段 ----
    @JsonProperty("supervision_point")
    private String supervisionPoint;

    @JsonProperty("problem_description")
    private String problemDescription;

    // ---- 内嵌问题项 ----
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueItem {
        @JsonProperty("issue_category")
        private String category;

        @JsonProperty("issue_type")
        private String type;
    }
}
