package com.hubu.caseassistant.service;

import com.hubu.caseassistant.dto.CaseListItemDto;
import com.hubu.caseassistant.dto.ProblemListItemDto;
import com.hubu.caseassistant.dto.ReportDto;
import com.hubu.caseassistant.dto.lcba.AiScenarioDto;
import com.hubu.caseassistant.model.Case;
import com.hubu.caseassistant.model.ProsecutorProfile;
import com.hubu.caseassistant.repository.CaseRepository;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CaseAnalysisService {
    String startAnalysis(MultipartFile[] files);

    ProsecutorProfile getProsecutorProfileById(UUID prosecutorId);

    List<Map<String, String>> getAllProsecutorsSummary();

    List<ReportDto> getAllReports();

    /**
     * 根据办案人和问题类别获取案件列表
     * @param prosecutorId 办案人ID
     * @param issueCategory 问题类别 (如: evidence, fact, law, procedure)
     * @return 案件列表
     */
    List<CaseListItemDto> getCasesByProsecutorAndIssueCategoryAndIssueType(String prosecutorId, String issueCategory, String issueType);

    /**
     * 获取单个案件的详细信息
     * @param caseId 案件ID
     * @return 案件实体
     */
    Case getCaseDetailsById(String caseId);

    /**
     * 获取案件关联的电子卷宗
     * @param caseId 案件ID
     * @return 文件资源
     */
    Resource getCaseDocument(String caseId) throws IOException;
    /**
     * 根据报告ID列表启动分析，并返回检察官简报
     * @param reportIds 报告ID列表
     * @return 检察官简报列表 (Map中包含 prosecutorId 和 name)
     */
    List<Map<String, String>> startAnalysisFromReports(List<String> reportIds);

    /**
     * 根据报告ID列表启动流式分析
     * @param reportIds 报告ID列表
     * @param emitter SSE发射器，用于向客户端发送实时事件
     */
    void startStreamingAnalysis(List<String> reportIds, SseEmitter emitter);

    void analyzeCaseFromClientPlatform(String bmsah);

    /**
     * 增加指定AI场景的使用次数
     * @param scenarioKey 场景的唯一标识
     */
    void incrementScenarioUsage(String scenarioKey);

    /**
     * 获取所有AI场景及其信息
     * @return AI场景的DTO列表
     */
    List<AiScenarioDto> getAllScenarios();

//    List<CaseListItemDto> getAllCasesByProsecutorId(String prosecutorId);

    List<ProblemListItemDto> getProblemListByProsecutorId(String prosecutorId);
    AiScenarioDto toggleFavorite(String scenarioKey);
    List<AiScenarioDto> getFavoriteScenarios();
    /**
     * 更新案件的部门受案号
     * @param caseId 案件ID
     * @param departmentCaseNumber 新的部门受案号
     * @return 更新后的案件信息
     */
    Case updateDepartmentCaseNumber(String caseId, String departmentCaseNumber);

}
