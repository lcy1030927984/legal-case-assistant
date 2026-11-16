package com.hubu.caseassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubu.caseassistant.dto.DifyWorkflowOutput;
import com.hubu.caseassistant.model.Case;
import com.hubu.caseassistant.model.ProsecutorProfile;
import com.hubu.caseassistant.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisPipelineService {

    private final ProsecutorProfileService profileService;
    private final CaseRepository caseRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    @Retryable(value = {DataIntegrityViolationException.class,
                        ObjectOptimisticLockingFailureException.class},
               maxAttempts = 3, backoff = @Backoff(delay = 200))
    public void saveProfileAndCaseFromDify(DifyWorkflowOutput output, String filenameOnDisk) {
        String undertakerName = output.getUndertaker();
        String caseName = defaultIfNull(output.getCaseName(), "未知案件");

        if (undertakerName == null || undertakerName.isEmpty() || "未知案件".equals(caseName)) {
            log.warn("Dify响应中未找到承办人或案件名称，跳过此条记录。");
            return;
        }

        log.info("发现新案件: [{}] (承办人: {})，正在进行数据统计和保存...", caseName, undertakerName);
        ProsecutorProfile profile = profileService.findOrCreate(undertakerName);
        String handlerId = profile.getProsecutorId().toString();

        // 画像的 caseCount / department / tenure 由 Dify 落库时维护（Dify 知道办了几个案）。
        // 问题计数（*FlawCount/*ErrorCount）不再在此处增量——交给 Agent 回写 CaseIssue 时
        // 统一更新，避免 Dify 落库 + Agent 回写双写导致重复计数（方案 §3.3 单一数据源）。
        profile.setCaseCount(profile.getCaseCount() + 1);
        profile.setDepartment(defaultIfNull(output.getOrganizer(), "未知单位"));
        profile.setTenure(output.getTenure() != null ? output.getTenure() : 0);

        // Dify 的 issues 仅用于决定建几行 Case（按问题维度填主表 issueCategory/Type），
        // 不再用于画像计数。
        Set<String> uniqueIssues = new HashSet<>();
        List<DifyWorkflowOutput.IssueItem> issues = output.getIssues();

        if (issues != null && !issues.isEmpty()) {
            log.info("报告 [{}] 中共发现 {} 个问题点，按维度建 Case 记录...", filenameOnDisk, issues.size());
            for (DifyWorkflowOutput.IssueItem issue : issues) {
                String category = defaultIfNull(issue.getCategory(), "none");
                String type = defaultIfNull(issue.getType(), "none");
                if (!"none".equals(category) && !"none".equals(type)) {
                    uniqueIssues.add(category + ":" + type);
                }
            }
        } else {
            String category = defaultIfNull(output.getIssueCategory(), "none");
            String type = defaultIfNull(output.getIssueType(), "none");
            if (!"none".equals(category) && !"none".equals(type)) {
                log.info("发现单个问题点: {}:{}", category, type);
                uniqueIssues.add(category + ":" + type);
            }
        }

        if (uniqueIssues.isEmpty()) {
            Case saved = createCaseRecord(output, filenameOnDisk, caseName, handlerId, undertakerName, "none", "none");
            publishCasePersisted(saved, undertakerName, output, filenameOnDisk);
        } else {
            boolean first = true;
            for (String uniqueIssue : uniqueIssues) {
                String[] parts = uniqueIssue.split(":");
                Case saved = createCaseRecord(output, filenameOnDisk, caseName, handlerId, undertakerName, parts[0], parts[1]);
                // 一个文件可能拆出多行 Case（按问题维度），Agent 评查只挂在第一条主记录上
                if (first) {
                    publishCasePersisted(saved, undertakerName, output, filenameOnDisk);
                    first = false;
                }
            }
        }

        profileService.save(profile);
        log.info("成功更新了办案人员 [{}] 的画像信息。", profile.getName());
    }

    private Case createCaseRecord(DifyWorkflowOutput output, String uniqueFilename, String caseName,
                                  String handlerId, String handlerName, String category, String type) {
        Case newCase = new Case();

        newCase.setJzwjh(output.getDossierFileNumber());
        newCase.setCaseName(caseName);
        newCase.setHandlerId(handlerId);
        newCase.setHandlerName(handlerName);
        newCase.setDocumentUrl(uniqueFilename);
        newCase.setHandlingUnit(defaultIfNull(output.getOrganizer(), "未知单位"));
        newCase.setCaseStatus(output.getCaseStatus());
        newCase.setCaseSummary(output.getCaseSummary());

        newCase.setIssueCategory(category);
        newCase.setIssueType(type);

        newCase.setDepartmentCaseNumber(output.getDepartmentCaseNumber());
        newCase.setTransferUnit(output.getTransferUnit());
        newCase.setTransferReason(output.getTransferReason());
        newCase.setTransferOtherReason(output.getTransferOtherReason());
        newCase.setConclusionSituation(output.getConclusionSituation());
        newCase.setConclusionReason(output.getConclusionReason());
        newCase.setConclusionOtherReason(output.getConclusionOtherReason());
        newCase.setConclusionAmount(output.getConclusionAmount());
        newCase.setIsPleaBargain(output.getIsPleaBargain());
        newCase.setProsecutionOpinionBookNumber(output.getProsecutionOpinionBookNumber());
        newCase.setFirstTrialResult(output.getFirstTrialResult());
        newCase.setProbationPeriod(output.getProbationPeriod());
        newCase.setFirstTrialAnnouncementSentence(output.getFirstTrialAnnouncementSentence());

        newCase.setAcceptanceDate(parseDate(output.getAcceptanceDate()));
        newCase.setConclusionDate(parseDate(output.getConclusionDate()));
        newCase.setProcessingDays(output.getProcessingDays() != null ? output.getProcessingDays() : 0);
        newCase.setFirstTrialAnnouncementDate(parseDate(output.getFirstTrialAnnouncementDate()));

        newCase.setIssueAnalysisContent(defaultIfNull(output.getIssueAnalysisContent(), ""));
        newCase.setIssueHandlingStatusContent(defaultIfNull(output.getIssueHandlingStatusContent(), ""));
        newCase.setEvidentiaryMaterialsContent(defaultIfNull(output.getEvidentiaryMaterialsContent(), ""));
        newCase.setThinkingProcessReport(defaultIfNull(output.getThinkingProcessReport(), ""));

        newCase.setSupervisionPoint(output.getSupervisionPoint());
        newCase.setProblemDescription(output.getProblemDescription());
        newCase.setCreationTime(LocalDateTime.now());

        caseRepository.save(newCase);
        log.info("为案件 [{}] 创建了一个问题记录，类别: {}, 类型: {}", caseName, category, type);
        return newCase;
    }

    /**
     * 发布案件落库事件，由 {@link AgentEvaluationTrigger} 在事务提交后异步触发 Agent 评查。
     * 在事务内调用安全——事件监听器配置为 AFTER_COMMIT，不会阻塞或回滚本事务。
     */
    private void publishCasePersisted(Case saved, String handlerName,
                                      DifyWorkflowOutput output, String filenameOnDisk) {
        if (saved == null || saved.getId() == null) {
            return;
        }
        eventPublisher.publishEvent(
                new CasePersistedEvent(saved.getId(), handlerName, output, filenameOnDisk));
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            log.warn("无法解析日期字符串: '{}'，将使用null。期望格式: yyyy-MM-dd", dateString);
            return null;
        }
    }

    private static String defaultIfNull(String value, String fallback) {
        return value != null ? value : fallback;
    }

    public DifyWorkflowOutput parseWorkflowOutput(String responseJson) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseJson);
        JsonNode outputsNode = rootNode.path("outputs");
        if (outputsNode.isMissingNode() || !outputsNode.isObject()) {
            outputsNode = rootNode.path("data").path("outputs");
        }
        if (outputsNode.isMissingNode()) {
            throw new RuntimeException("在Dify响应中未找到 'outputs' 节点。响应: " + responseJson);
        }
        return objectMapper.treeToValue(outputsNode, DifyWorkflowOutput.class);
    }
}
