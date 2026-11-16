package com.hubu.caseassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubu.caseassistant.dto.DifyWorkflowOutput;
import com.hubu.caseassistant.dto.agent.AgentAnalyzeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 监听 {@link CasePersistedEvent}，在 Dify 落库事务提交后异步触发 Agent 评查。
 *
 * <p>关键设计：用 {@link TransactionalEventListener}(AFTER_COMMIT) 保证只在 Dify 结果
 * 成功落库后才跑 Agent；用独立线程池异步执行，Agent 失败不回滚 Dify 落库、不阻塞主流程。
 *
 * <p>这是 Agent 从“旁路”翻转为“主线”的触发点（方案 §7.2 P1）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentEvaluationTrigger {

    private final AgentEvaluationService agentEvaluationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Agent 评查专用线程池：与 SSE 线程池隔离，避免评查长耗时拖累流式推送 */
    private final ThreadPoolExecutor agentExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            r -> {
                Thread t = new Thread(r, "agent-eval-" + COUNTER.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /** 主流程触发的评查默认场景：评查通（全项评查） */
    private static final String DEFAULT_SCENARIO_KEY = "pingcha_tong";

    /**
     * Dify 落库事务提交后触发。若用普通 @EventListener 会在事务内同步执行，
     * 这里必须 AFTER_COMMIT，否则 Agent 失败会回滚已落库的 Dify 结果。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCasePersisted(CasePersistedEvent event) {
        agentExecutor.execute(() -> {
            try {
                runEvaluation(event);
            } catch (Exception e) {
                log.error("案件 [{}] Agent 评查异步执行失败: {}", event.getCaseId(), e.getMessage(), e);
            }
        });
    }

    private void runEvaluation(CasePersistedEvent event) {
        DifyWorkflowOutput output = event.getDifyOutput();
        Map<String, Object> difyResult;
        try {
            difyResult = objectMapper.convertValue(output, Map.class);
        } catch (Exception e) {
            log.warn("案件 [{}] DifyOutput 转 Map 失败，跳过 Agent 评查: {}", event.getCaseId(), e.getMessage());
            return;
        }

        AgentAnalyzeRequest request = new AgentAnalyzeRequest(
                "请按评查模板逐项判定本案质量问题，输出结构化问题清单。",
                difyResult);
        // 主流程触发的评查默认走"评查通"场景；该场景的 checklist 由 AgentEvaluationService 加载注入
        log.info("案件 [{}] 开始异步 Agent 评查（承办人: {}, 场景: {}）",
                event.getCaseId(), event.getHandlerName(), DEFAULT_SCENARIO_KEY);
        agentEvaluationService.evaluateAndWriteBack(
                event.getCaseId(), request, event.getHandlerName(), DEFAULT_SCENARIO_KEY);
    }
}
