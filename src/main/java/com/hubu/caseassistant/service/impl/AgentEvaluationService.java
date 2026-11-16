package com.hubu.caseassistant.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubu.caseassistant.dto.agent.AgentAnalyzeRequest;
import com.hubu.caseassistant.dto.agent.AgentEvaluationResult;
import com.hubu.caseassistant.dto.agent.AgentIssueItem;
import com.hubu.caseassistant.model.AiScenario;
import com.hubu.caseassistant.model.Case;
import com.hubu.caseassistant.model.CaseIssue;
import com.hubu.caseassistant.model.ProsecutorProfile;
import com.hubu.caseassistant.repository.AiScenarioRepository;
import com.hubu.caseassistant.repository.CaseIssueRepository;
import com.hubu.caseassistant.repository.CaseRepository;
import com.hubu.caseassistant.service.client.AgentServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Agent 评查产出的结构化回写服务（方案 §6.1 / §3.1 / §3.3 的 Java 侧落地）。
 *
 * <p>职责：把 {@link AgentEvaluationResult#getIssues()} 写入 {@link CaseIssue} 子表，
 * 回填 {@link Case} 主表的主问题标签（取最严重项），并聚合到 {@link ProsecutorProfile}。
 *
 * <p>这是 Agent 从“旁路”翻转为“主线产出源”的落地点——Agent 判定结果经此写库后，
 * 画像和问题清单的唯一来源就是 Agent，存在感立起来（方案 §8 P0）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentEvaluationService {

    private final AgentServiceClient agentClient;
    private final CaseRepository caseRepository;
    private final CaseIssueRepository caseIssueRepository;
    private final ProsecutorProfileService profileService;
    private final AiScenarioRepository aiScenarioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询某案件已落库的结构化评查结果，供问答助手作为上下文。
     *
     * <p>方案 §6.2：问答助手先查 CaseIssue，有则带上下文秒答，无则即时评查。
     * 本方法提供“有”的查询能力。
     *
     * @return 该案件全部 CaseIssue 的可读摘要列表；无则空表
     */
    @Transactional(readOnly = true)
    public List<CaseIssue> getEvaluationContext(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return caseIssueRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
    }

    /** 判断某案件是否已有 Agent 评查产出 */
    @Transactional(readOnly = true)
    public boolean hasEvaluation(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return false;
        }
        return caseIssueRepository.existsByCaseIdAndSource(caseId, "agent");
    }

    /**
     * 将某案件的评查上下文拼进用户消息，供问答助手“就本案追问”。
     *
     * <p>方案 §6.2：有 CaseIssue 则带上下文（让 Agent 不必重读卷宗秒答）；无则原样返回。
     */
    @Transactional(readOnly = true)
    public String enrichMessageWithContext(String caseId, String message) {
        if (caseId == null || caseId.isBlank()) {
            return message;
        }
        List<CaseIssue> issues = getEvaluationContext(caseId);
        if (issues.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n\n以下是该案件已有的结构化评查结果，请基于此回答：\n");
        for (int i = 0; i < issues.size(); i++) {
            CaseIssue ci = issues.get(i);
            sb.append(String.format(
                    "%d. [%s/%s/严重度:%s] %s — %s\n",
                    i + 1,
                    defaultIfNull(ci.getCategory(), "未分类"),
                    defaultIfNull(ci.getIssueType(), ""),
                    defaultIfNull(ci.getSeverity(), ""),
                    defaultIfNull(ci.getTitle(), ""),
                    defaultIfNull(ci.getDescription(), "")));
        }
        return sb.toString();
    }

    /**
     * 按 scenarioKey 加载该 AI 场景关联的评查 checklist。
     *
     * <p>方案 §6.3：AiScenario 接 Agent 模式，每个场景可配置自己的 checklist 子集。
     * 未配置（checklistConfig 为 null）或解析失败时返回 null，Python 侧用默认四项 checklist。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadChecklist(String scenarioKey) {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            return null;
        }
        return aiScenarioRepository.findByScenarioKey(scenarioKey)
                .map(AiScenario::getChecklistConfig)
                .filter(cfg -> cfg != null && !cfg.isBlank())
                .map(cfg -> {
                    try {
                        return objectMapper.readValue(cfg, new TypeReference<List<Map<String, Object>>>() {});
                    } catch (Exception e) {
                        log.warn("场景 [{}] checklistConfig 解析失败，用默认: {}", scenarioKey, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    // 严重程度排序权重，用于挑出“最严重”的问题回填 Case 主问题字段
    private static final Comparator<AgentIssueItem> SEVERITY_DESC =
            Comparator.comparingInt(AgentEvaluationService::severityWeight).reversed();

    /**
     * 触发 Agent 评查并把结构化结果回写到案件。
     *
     * <p>调用 Python 评查员 → 解析 issues → 写 CaseIssue → 回填 Case 主问题 → 聚合画像。
     * 任一环节的异常都被捕获并记日志，不抛断主流程（评查失败不应让整个分析流程崩掉）。
     *
     * @param caseId       案件 ID
     * @param request      Agent 请求体（含 difyResult 等上下文）
     * @param handlerName  承办人姓名（用于画像聚合，缺失时从 Case 取）
     * @param scenarioKey  AI 场景标识，据此取该场景的 checklist 注入 Agent（方案 §6.3）
     */
    @Transactional
    public void evaluateAndWriteBack(String caseId, AgentAnalyzeRequest request,
                                     String handlerName, String scenarioKey) {
        // 按 scenarioKey 取该场景的 checklist 注入 request（Scenario 接 Agent 模式）
        List<Map<String, Object>> checklist = loadChecklist(scenarioKey);
        if (checklist != null) {
            request.setChecklist(checklist);
        }
        request.setCaseId(caseId);

        AgentEvaluationResult result;
        try {
            result = agentClient.evaluate(request);
        } catch (Exception e) {
            log.warn("案件 [{}] 调用 Agent 评查失败，跳过结构化回写: {}", caseId, e.getMessage());
            return;
        }

        if (result == null) {
            log.warn("案件 [{}] Agent 评查返回空结果，跳过回写。", caseId);
            return;
        }

        List<AgentIssueItem> issues = result.getIssues();
        if (issues == null || issues.isEmpty()) {
            // 过渡期 Agent 可能仍输出散文 answer，没有结构化 issues
            log.info("案件 [{}] Agent 未产出结构化 issues（traceId={}），仅保存散文结果。", caseId, result.getTraceId());
            return;
        }

        Case caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            log.warn("案件 [{}] 不存在，无法回写 Agent 评查结果。", caseId);
            return;
        }

        // 1) 批量写 CaseIssue 子表
        for (AgentIssueItem issue : issues) {
            CaseIssue entity = toEntity(issue, caseId, result.getTraceId());
            caseIssueRepository.save(entity);
        }
        log.info("案件 [{}] 已写入 {} 条结构化问题（traceId={}）", caseId, issues.size(), result.getTraceId());

        // 2) 取最严重项回填 Case 主问题标签（保证老接口 findByIssueCategory 不破）
        AgentIssueItem worst = issues.stream().max(SEVERITY_DESC).orElse(issues.get(0));
        caseEntity.setIssueCategory(defaultIfNull(worst.getCategory(), caseEntity.getIssueCategory()));
        caseEntity.setIssueType(defaultIfNull(worst.getIssueType(), caseEntity.getIssueType()));
        caseRepository.save(caseEntity);

        // 3) 聚合到检察官画像
        if (handlerName == null || handlerName.isBlank()) {
            handlerName = caseEntity.getHandlerName();
        }
        if (handlerName != null && !handlerName.isBlank()) {
            ProsecutorProfile profile = profileService.findOrCreate(handlerName);
            for (AgentIssueItem issue : issues) {
                String category = defaultIfNull(issue.getCategory(), "none");
                String type = mapIssueType(issue.getIssueType());
                profileService.updateIssueCounts(profile, category, type);
            }
            profileService.save(profile);
            log.info("已为检察官 [{}] 聚合 {} 条 Agent 评查问题到画像。", handlerName, issues.size());
        }
    }

    private CaseIssue toEntity(AgentIssueItem issue, String caseId, String traceId) {
        CaseIssue entity = new CaseIssue();
        entity.setCaseId(caseId);
        entity.setCategory(issue.getCategory());
        entity.setIssueType(issue.getIssueType());
        entity.setSeverity(issue.getSeverity());
        entity.setTitle(issue.getTitle());
        entity.setDescription(issue.getDescription());
        entity.setCitations(toJson(issue.getCitations()));
        entity.setSimilarCaseIds(toJson(issue.getSimilarCaseIds()));
        entity.setTraceId(traceId);
        entity.setTraceStepIds(toJson(issue.getTraceStepIds()));
        entity.setSource("agent");
        return entity;
    }

    /** Agent 的 issueType 是中文（瑕疵/错误/存疑），映射到画像统计使用的 flaw/error */
    private String mapIssueType(String issueType) {
        if (issueType == null) {
            return "none";
        }
        String t = issueType.trim();
        if (t.contains("错误")) {
            return "error";
        }
        if (t.contains("瑕疵") || t.contains("存疑")) {
            return "flaw";
        }
        // 兼容已经是英文的情况
        return t;
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("序列化 JSON 失败，落空: {}", e.getMessage());
            return null;
        }
    }

    private static int severityWeight(AgentIssueItem issue) {
        if (issue == null || issue.getSeverity() == null) {
            return 0;
        }
        return switch (issue.getSeverity().trim()) {
            case "高" -> 3;
            case "中" -> 2;
            case "低" -> 1;
            default -> 0;
        };
    }

    private static String defaultIfNull(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
