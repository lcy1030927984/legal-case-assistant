package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.dto.DifyWorkflowOutput;

/**
 * 案件落库事件 —— 在 Dify 结果写入 Case 后发布，触发 Agent 评查。
 *
 * <p>设计要点（见 docs/agent-redesign.md §7.2）：
 * <ul>
 *   <li>在 {@code AnalysisPipelineService.saveProfileAndCaseFromDify} 事务内发布；</li>
 *   <li>由 {@code AgentEvaluationTrigger} 用 {@code @TransactionalEventListener(AFTER_COMMIT)}
 *       在事务提交后接收，避免 Agent 长耗时阻塞事务、或 Agent 失败回滚 Dify 落库；</li>
 *   <li>监听器异步执行 Agent 评查，结果回写 CaseIssue，不阻塞主流程。</li>
 * </ul>
 */
public class CasePersistedEvent {
    private final String caseId;
    private final String handlerName;
    private final DifyWorkflowOutput difyOutput;
    private final String filenameOnDisk;

    public CasePersistedEvent(String caseId, String handlerName,
                               DifyWorkflowOutput difyOutput, String filenameOnDisk) {
        this.caseId = caseId;
        this.handlerName = handlerName;
        this.difyOutput = difyOutput;
        this.filenameOnDisk = filenameOnDisk;
    }

    public String getCaseId() { return caseId; }
    public String getHandlerName() { return handlerName; }
    public DifyWorkflowOutput getDifyOutput() { return difyOutput; }
    public String getFilenameOnDisk() { return filenameOnDisk; }
}
