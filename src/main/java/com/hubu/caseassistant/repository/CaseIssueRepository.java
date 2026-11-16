package com.hubu.caseassistant.repository;

import com.hubu.caseassistant.model.CaseIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 案件结构化问题子表 Repository。
 *
 * <p>支持两类查询：
 * <ul>
 *   <li>按案件取该案全部问题（问答助手上下文、前端 Case 详情叠加）。</li>
 *   <li>按承办人聚合画像（{@code ProsecutorProfile} 统计源切换后的核心查询，见方案 §3.3）。</li>
 * </ul>
 */
@Repository
public interface CaseIssueRepository extends JpaRepository<CaseIssue, String> {

    /** 按案件 ID 查全部问题，按创建时间排序 */
    List<CaseIssue> findByCaseIdOrderByCreatedAtAsc(String caseId);

    /** 判断某案件是否已有 Agent 评查产出（问答助手据此决定是否即时评查） */
    boolean existsByCaseIdAndSource(String caseId, String source);

    /**
     * 按承办人聚合各类问题的数量 —— 画像统计的目标态查询（方案 §3.3）。
     * 返回行：[handlerName, category, issueType, severity, count]
     *
     * <p>注意：此查询 JOIN cases 表，依赖 cases.handler_name 字段。
     * 历史无 CaseIssue 的案件不会出现在结果中，画像需配合迁移策略（见方案 §9.5）。
     */
    @Query("""
            select c.handlerName, ci.category, ci.issueType, ci.severity, count(ci)
            from CaseIssue ci
            join Case c on ci.caseId = c.id
            where c.handlerName in :handlerNames
            group by c.handlerName, ci.category, ci.issueType, ci.severity
            """)
    List<Object[]> aggregateByHandlerNames(@Param("handlerNames") java.util.Collection<String> handlerNames);
}
