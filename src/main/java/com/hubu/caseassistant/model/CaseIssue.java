package com.hubu.caseassistant.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案件结构化问题子表 —— Agent 评查判定结果的落地处。
 *
 * <p>背景与定位见 {@code docs/agent-redesign.md} §3.1。
 * <p>设计要点：
 * <ul>
 *   <li>不动 {@link Case} 主表 70+ 字段，老的前端 Tab 文本渲染仍走主表，本子表叠加结构化判定。</li>
 *   <li>一个案件可挂多条 CaseIssue（主表只能挂单值标签），解决“一案多问题”无处安放。</li>
 *   <li>{@code source} 标记来源是 agent 还是 dify，便于过渡期与历史数据并存。</li>
 *   <li>{@code citations} / {@code similarCaseIds} / {@code traceStepIds} 以 JSON 字符串存储，
 *       过渡期不依赖 ORM 关联，降低改动面。</li>
 * </ul>
 */
@Entity
@Table(name = "case_issues")
@Data
public class CaseIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "varchar(36)")
    private String id;

    /** 关联的案件 ID，对应 cases.id */
    @Column(name = "case_id", nullable = false, columnDefinition = "varchar(36)")
    private String caseId;

    /** 问题类别：evidence / fact / law / procedure */
    @Column(name = "category")
    private String category;

    /** 问题类型：瑕疵 / 错误 / 存疑 */
    @Column(name = "issue_type")
    private String issueType;

    /** 严重程度：高 / 中 / 低 */
    @Column(name = "severity")
    private String severity;

    /** 问题一句话标题 */
    @Column(name = "title")
    private String title;

    /** 详细描述（来自 Agent 判定） */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 法条引用，JSON 数组字符串，如 ["《刑事诉讼法》第55条"]。
     * 过渡期来自评查模板内置标准；RAG 上线后改为 legal_doc_search 检索结果。
     */
    @Column(name = "citations", columnDefinition = "TEXT")
    private String citations;

    /** 参考类案 ID，JSON 数组字符串，如 ["case-id-1","case-id-2"] */
    @Column(name = "similar_case_ids", columnDefinition = "TEXT")
    private String similarCaseIds;

    /** 指向完整推理链 traceId */
    @Column(name = "trace_id")
    private String traceId;

    /** 该问题对应的推理步骤 ID，JSON 数组字符串 */
    @Column(name = "trace_step_ids", columnDefinition = "TEXT")
    private String traceStepIds;

    /** 来源标记：agent / dify */
    @Column(name = "source")
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
