package com.hubu.caseassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubu.caseassistant.dto.CaseListItemDto;
import com.hubu.caseassistant.dto.DifyFileUploadResponse;
import com.hubu.caseassistant.dto.DifyWorkflowOutput;
import com.hubu.caseassistant.dto.ProblemListItemDto;
import com.hubu.caseassistant.dto.ReportDto;
import com.hubu.caseassistant.dto.lcba.AiScenarioDto;
import com.hubu.caseassistant.dto.lcba.AjwsjzxxData;
import com.hubu.caseassistant.model.Case;
import com.hubu.caseassistant.model.ProsecutorProfile;
import com.hubu.caseassistant.model.Report;
import com.hubu.caseassistant.repository.CaseRepository;
import com.hubu.caseassistant.repository.ReportRepository;
import com.hubu.caseassistant.service.CaseAnalysisService;
import com.hubu.caseassistant.service.client.DifyApiClient;
import com.hubu.caseassistant.service.client.SupconFeignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.jodconverter.core.DocumentConverter;
import org.springframework.beans.factory.ObjectProvider;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaseAnalysisServiceImpl implements CaseAnalysisService {

    private final ReportRepository reportRepository;
    private final CaseRepository caseRepository;
    private final DifyApiClient difyApiClient;
    private final com.hubu.caseassistant.config.DifyApiProperties difyApiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SupconFeignService supconFeignService;
    // 用 ObjectProvider 包装，使 JodConverter 禁用（未装 LibreOffice）时仍能启动；
    // 用时判空，未配置则 .doc 转 PDF 静默跳过
    private final ObjectProvider<DocumentConverter> documentConverterProvider;
    private final ScenarioService scenarioService;
    private final ProsecutorProfileService profileService;
    private final CaseQueryService caseQueryService;
    private final FileStorageService fileStorageService;
    private final AnalysisPipelineService pipelineService;

    @Override
    @Transactional
    public String startAnalysis(MultipartFile[] files) {
        String analysisId = "analysis-" + UUID.randomUUID().toString();
        log.info("开始分析任务ID: {}, 共收到 {} 个文件进行处理。", analysisId, files.length);

        for (MultipartFile multipartFile : files) {
            if (multipartFile.isEmpty()) {
                continue;
            }

            String originalFilename = multipartFile.getOriginalFilename();

            // 【更新】文件类型过滤逻辑：只允许 .doc, .docx, .pdf 文件通过
            if (originalFilename == null) {
                log.warn("文件名为空，已跳过");
                continue;
            }

            String lowerCaseFilename = originalFilename.toLowerCase();
            if (!lowerCaseFilename.endsWith(".doc") &&
                    !lowerCaseFilename.endsWith(".docx") &&
                    !lowerCaseFilename.endsWith(".pdf")) {
                log.warn("检测到不支持的文件类型 (仅支持 .doc, .docx, .pdf)，已跳过: {}", originalFilename);
                continue;
            }

            File savedFileWithUniqueName = null;
            File fileToUpload = null;

            try {
                savedFileWithUniqueName = fileStorageService.convertMultiPartToFile(multipartFile);
                fileToUpload = savedFileWithUniqueName;
                // 【关键修改】从保存后的文件中获取其在磁盘上的唯一名称
                String uniqueFilenameOnDisk = savedFileWithUniqueName.getName();
                // 基于文件名+内容哈希的去重检查
                String fileHash = fileStorageService.calculateMd5(savedFileWithUniqueName);

                if (fileHash != null) {
                    if (reportRepository.existsByReportNameAndFileHash(originalFilename, fileHash)) {
                        log.warn("文件 [{}] (MD5: {}) 已被分析过，跳过处理。", originalFilename, fileHash);
                        savedFileWithUniqueName.delete();
                        continue;
                    }
                } else {
                    log.warn("无法计算文件 [{}] 的MD5哈希值，跳过去重检查，直接处理。", originalFilename);
                }

                String reportNameToSave = originalFilename;

                if (originalFilename.toLowerCase().endsWith(".doc")) {
                    log.info("发现.doc文件 [{}], 准备使用 JodConverter 转换为pdf...", originalFilename);

                    File pdfFile = new File(savedFileWithUniqueName.getParent(),
                            savedFileWithUniqueName.getName().replaceFirst("(?i)\\.doc$", ".pdf"));
                    DocumentConverter converter = documentConverterProvider.getIfAvailable();
                    if (converter == null) {
                        log.error("!!! JodConverter 未启用（LibreOffice 未安装或 jodconverter.local.enabled=false），无法转换 .doc 文件，跳过: {}", originalFilename);
                        savedFileWithUniqueName.delete();
                        continue;
                    }
                    try {
                        converter.convert(savedFileWithUniqueName).to(pdfFile).execute();

                        log.info("文件使用 JodConverter 成功转换为: {}", pdfFile.getName());

                        fileToUpload = pdfFile; // 将待上传文件指向新生成的 .pdf文件
                        // 【在此处添加关键修复代码】
                        uniqueFilenameOnDisk = pdfFile.getName(); // 更新变量，确保后续保存的是PDF文件名
                        // 在转换成功后，立即更新 originalFilename 变量的值
                        reportNameToSave = reportNameToSave.replaceFirst("(?i)\\.doc$", ".pdf");
                    } catch (Exception conversionEx) {
                        log.error("!!! JodConverter 转换 .doc 到 .pdf 失败！请检查LibreOffice是否已安装且服务正常。将跳过此文件。错误: ", conversionEx);
                        savedFileWithUniqueName.delete();
                        continue;
                    }
                }

                log.info("最终准备上传的文件是: '{}'", fileToUpload.getName());


                DifyFileUploadResponse uploadResponse = difyApiClient.uploadFile(fileToUpload);
                String fileId = uploadResponse.getId();

                Report report = new Report();
                // reportName 存储原始文件名，用于前端展示
                report.setReportName(reportNameToSave);
                // 新增的字段，存储磁盘上的唯一文件名
                report.setUniqueFilenameOnDisk(uniqueFilenameOnDisk);
                report.setFileHash(fileHash); // 保存文件内容的哈希值
                report.setCreationDate(LocalDateTime.now());
                report.setFileId(fileId);
                reportRepository.save(report);
                log.info("报告 [{}] 信息已保存到数据库，关联的 Dify File ID 为: {}", report.getReportName(), fileId);

                long delayMs = difyApiProperties.getWorkflowTriggerDelayMs();
                log.info("等待 {}ms 后触发Dify工作流...", delayMs);
                Thread.sleep(delayMs);
                String workflowResultJson = difyApiClient.triggerWorkflow(fileId);
                log.info("【Dify工作流原始响应】: {}", workflowResultJson);

                DifyWorkflowOutput output;
                try {
                    output = pipelineService.parseWorkflowOutput(workflowResultJson);
                } catch (Exception parseEx) {
                    log.error("解析Dify工作流输出失败: {}", parseEx.getMessage());
                    continue;
                }

                // 通过 self 代理调用以确保 @Transactional 生效
                pipelineService.saveProfileAndCaseFromDify(output, uniqueFilenameOnDisk);


            } catch (Exception e) {
                log.error("处理文件 {} 时发生错误: ", originalFilename, e);
                throw new RuntimeException("处理文件失败: " + originalFilename, e);
            } finally {
//                if (originalTempFile != null && originalTempFile.exists()) {
//                    originalTempFile.delete();
//                }
//                if (fileToUpload != null && fileToUpload.exists() && !fileToUpload.equals(originalTempFile)) {
//                    fileToUpload.delete();
//                }
                log.info("文件处理流程结束，文件 [{}] 和相关转换文件将被保留在存储目录中。", multipartFile.getOriginalFilename());
            }
        }
        return analysisId;
    }

    /**
     * 根据案件编号，从客户平台获取所有文书并进行分析
     *
     * @param bmsah 部门受案号
     */
    public void analyzeCaseFromClientPlatform(String bmsah) {
        // 1. 调用接口获取文书列表
        log.info("开始从客户平台为案号 [{}] 拉取文书列表...", bmsah);
        List<AjwsjzxxData> originalDocumentList = supconFeignService.getCaseDocumentList(bmsah);

        if (originalDocumentList == null || originalDocumentList.isEmpty()) {
            log.warn("案号 [{}] 没有找到任何文书，任务终止。", bmsah);
            return;
        }


        log.info("从平台共获取到 {} 条文书/目录记录，开始根据文件名筛选...", originalDocumentList.size());
        // 【新增】根据文件名进行筛选
        List<AjwsjzxxData> documentList = originalDocumentList.stream()
                .filter(doc -> {
                    String filename = doc.getWjmc(); // 获取文件名
                    // 文件名不为空，且包含"案件报告" 或 "评查案件报告"
                    return filename != null && (filename.contains("案件报告") || filename.contains("评查案件报告"));
                })
                .collect(Collectors.toList());
        log.info("筛选后剩下 {} 条符合条件的文书记录。", documentList.size());

        // 使用筛选后的列表进行后续判断和处理
        if (documentList.isEmpty()) {
            log.warn("案号 [{}] 下没有文件名符合筛选条件的文书，任务终止。", bmsah);
            return;
        }

        log.info("为案号 [{}] 成功获取到 {} 条文书/目录记录，开始处理文件...", bmsah, documentList.size());
        List<MultipartFile> filesToAnalyze = new ArrayList<>();

        // 2. 遍历文书列表，下载文件
        // (这是一个简化的遍历，您可以根据需要实现递归遍历children)
        for (AjwsjzxxData docInfo : documentList) {
            // 假设 "0" 代表是文件, "1" 代表是目录
            if ("N".equals(docInfo.getSfml()) && docInfo.getJzwjh() != null) {
                try {
                    // 3. 根据文件号下载文件内容
                    byte[] fileBytes = supconFeignService.downloadCaseDocument(bmsah, docInfo.getJzwjh());

                    if (fileBytes != null) {
                        // 4. 将下载的字节数组转换为 MultipartFile 对象
                        MultipartFile multipartFile = new MockMultipartFile(
                                docInfo.getWjmc(), // 文件名
                                docInfo.getWjmc() + "." + docInfo.getWjkzm(), // 原始文件名 (带后缀)
                                "application/octet-stream", // 内容类型
                                fileBytes // 文件内容
                        );
                        filesToAnalyze.add(multipartFile);
                    }
                } catch (Exception e) {
                    log.error("处理文书 [{}] (文件号: {}) 时发生错误。", docInfo.getWjmc(), docInfo.getJzwjh(), e);
                }
            }
        }

        // 5. 调用您已有的分析服务进行处理
        if (!filesToAnalyze.isEmpty()) {
            log.info("共下载 {} 个文件，正在提交进行分析...", filesToAnalyze.size());
            // 将List转换为数组，并调用startAnalysis
            startAnalysis(filesToAnalyze.toArray(new MultipartFile[0]));
        } else {
            log.warn("案号 [{}] 下未找到可供分析的文件。", bmsah);
        }
    }

    /**
     * 增加指定AI场景的使用次数
     *
     * @param scenarioKey 场景的唯一标识
     */
    @Override
    public void incrementScenarioUsage(String scenarioKey) {
        scenarioService.incrementUsage(scenarioKey);
    }

    @Override
    public List<AiScenarioDto> getAllScenarios() {
        return scenarioService.getAll();
    }


    @Override
    public List<ProblemListItemDto> getProblemListByProsecutorId(String prosecutorId) {
        return caseQueryService.getProblemListByProsecutorId(prosecutorId);
    }

    @Override
    public AiScenarioDto toggleFavorite(String scenarioKey) {
        return scenarioService.toggleFavorite(scenarioKey);
    }

    @Override
    public List<AiScenarioDto> getFavoriteScenarios() {
        return scenarioService.getFavorites();
    }


    @Override
    public ProsecutorProfile getProsecutorProfileById(UUID prosecutorId) {
        return profileService.getById(prosecutorId);
    }

    @Override
    public List<Map<String, String>> getAllProsecutorsSummary() {
        return profileService.getAllSummaries();
    }

    @Override
    public List<ReportDto> getAllReports() {
        return caseQueryService.getAllReports();
    }

    @Override
    public List<CaseListItemDto> getCasesByProsecutorAndIssueCategoryAndIssueType(String prosecutorId, String issueCategory, String issueType) {
        return caseQueryService.getCasesByProsecutorAndIssue(prosecutorId, issueCategory, issueType);
    }

    @Override
    public Case getCaseDetailsById(String caseId) {
        return caseQueryService.getDetailsById(caseId);
    }

    @Override
    public Resource getCaseDocument(String caseId) throws IOException {
        return caseQueryService.getCaseDocument(caseId);
    }

    @Override
    @Transactional
    public List<Map<String, String>> startAnalysisFromReports(List<String> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            log.warn("报告ID列表为空，分析任务终止。");
            return Collections.emptyList();
        }

        log.info("开始基于报告ID列表的分析任务，共 {} 个报告。", reportIds.size());
        Set<String> affectedProsecutorNames = new HashSet<>();

        for (String reportId : reportIds) {
            reportRepository.findById(reportId).ifPresent(report -> {
                try {
                    String uniqueFilename = report.getUniqueFilenameOnDisk();
                    if (uniqueFilename == null || uniqueFilename.isBlank()) {
                        log.warn("报告 [{}] 没有记录其在磁盘上的唯一文件名，跳过分析。", report.getReportName());
                        return; // 跳过这个报告
                    }
                    //在调用Dify前，先检查缓存 ---
                    Optional<Case> cachedCaseOpt = caseRepository.findFirstByDocumentUrl(uniqueFilename);

                    if (cachedCaseOpt.isPresent()) {
                        // 1. 如果在数据库中找到了记录，说明已分析过
                        String handlerName = cachedCaseOpt.get().getHandlerName();
                        log.info("文件 [{}] 已被分析过，从数据库缓存加载结果。承办人: {}", report.getReportName(), handlerName);
                        affectedProsecutorNames.add(handlerName);
                        // 直接跳到下一个文件，不再执行Dify分析
                        return;
                    }

                    // 2. 如果缓存未命中，才执行Dify分析流程
                    log.info("文件 [{}] 未被分析过，开始调用Dify进行分析...", report.getReportName());
                    String fileId = report.getFileId();
                    if (fileId == null || fileId.isEmpty()) {
                        log.warn("报告 [{}] 没有关联的文件ID，跳过分析。", report.getReportName());
                        return;
                    }

                    String workflowResultJson = difyApiClient.triggerWorkflow(fileId);
                    log.info("【Dify工作流原始响应】 for report {}: {}", report.getReportName(), workflowResultJson);

                    DifyWorkflowOutput output;
                    try {
                        output = pipelineService.parseWorkflowOutput(workflowResultJson);
                    } catch (Exception parseEx) {
                        log.error("解析Dify工作流输出失败: {}", parseEx.getMessage());
                        return;
                    }

                    // 通过 self 代理调用以确保 @Transactional 生效
                    pipelineService.saveProfileAndCaseFromDify(output, uniqueFilename);

                    String undertakerName = output.getUndertaker();
                    if (undertakerName != null && !undertakerName.isEmpty()) {
                        affectedProsecutorNames.add(undertakerName);
                    }

                } catch (Exception e) {
                    log.error("处理报告 {} 时发生错误: ", report.getReportName(), e);
                }
            });
        }

        // 无论分析是新的还是来自缓存，最后都根据收集到的承办人姓名，统一查询并返回结果
        if (affectedProsecutorNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProsecutorProfile> profiles = profileService.findAllByNameIn(affectedProsecutorNames);
        return profiles.stream()
                .map(profile -> Map.of(
                        "prosecutorId", profile.getProsecutorId().toString(),
                        "name", profile.getName(),
                        "content", String.format("该检察官最近办理审查逮捕案件%d件，一审公诉案件%d件，总问题数%d个",
                                profile.getCaseCount(),
                                profile.getCaseCount(),
                                profile.getTotalFlawCount() + profile.getTotalErrorCount())
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void startStreamingAnalysis(List<String> reportIds, SseEmitter emitter) {
        log.info("开始SSE流式分析任务，共 {} 个报告。", reportIds.size());

        // 创建一个Set，只收集本次分析涉及的办案人员
        Set<String> affectedProsecutorNames = new HashSet<>();

        try {
            sendSseEvent(emitter, "start", "分析任务已启动...");

            for (String reportId : reportIds) {
                reportRepository.findById(reportId).ifPresent(report -> {
                    try {
                        sendSseEvent(emitter, "info", "正在处理报告: " + report.getReportName());

                        // 优化点1：缓存检查，如果命中，也要将办案人加入Set
                        Optional<Case> cachedCaseOpt = caseRepository.findFirstByDocumentUrl(report.getUniqueFilenameOnDisk());
                        if (cachedCaseOpt.isPresent()) {
                            String handlerName = cachedCaseOpt.get().getHandlerName();
                            affectedProsecutorNames.add(handlerName); // 加入Set
                            sendSseEvent(emitter, "info", "报告 " + report.getReportName() + " 已被分析过，从缓存加载。");
                            return;
                        }

                        // 请确保这个方法名与DifyApiClient中的定义一致
                        Flux<String> difyStream = difyApiClient.triggerWorkflowStreaming(report.getFileId());
                        StringBuilder fullResponse = new StringBuilder();

                        difyStream
                                .doOnNext(chunk -> {
                                    fullResponse.append(chunk);

                                    // 优化点2：统一使用JSON格式发送思考步骤
                                    sendSseEvent(emitter, "thinking_step", chunk);
                                })
                                .doOnComplete(() -> {
                                    log.info("报告 {} 的Dify流处理完成。", report.getReportName());
                                    try {
                                        // 【关键修改】从 report 对象中获取保存在磁盘上的唯一文件名
                                        String uniqueFilename = report.getUniqueFilenameOnDisk();
                                        if (uniqueFilename == null || uniqueFilename.isBlank()) {
                                            sendSseEvent(emitter, "error", "报告 " + report.getReportName() + " 缺少唯一文件名记录。");
                                            return;
                                        }


                                        DifyWorkflowOutput output;
                                        try {
                                            output = pipelineService.parseWorkflowOutput(fullResponse.toString());
                                        } catch (Exception parseEx) {
                                            sendSseEvent(emitter, "error", "报告 " + report.getReportName() + " 解析失败: " + parseEx.getMessage());
                                            return;
                                        }

                                        String handlerName = output.getUndertaker();
                                        if (handlerName != null && !handlerName.isEmpty()) {
                                            affectedProsecutorNames.add(handlerName);
                                        }
                                        pipelineService.saveProfileAndCaseFromDify(output, uniqueFilename);
                                        sendSseEvent(emitter, "info", "报告 " + report.getReportName() + " 的分析结果已保存。");
                                    } catch (Exception e) {
                                        log.error("保存Dify分析结果时出错", e);
                                        sendSseEvent(emitter, "error", "保存分析结果时出错: " + e.getMessage());
                                    }
                                })
                                .blockLast(Duration.ofMinutes(15));

                    } catch (Exception e) {
                        log.error("处理报告 {} 时发生内部错误: ", report.getReportName(), e);
                        sendSseEvent(emitter, "error", "处理报告 " + report.getReportName() + " 时出错: " + e.getMessage());
                    }
                });
            }

            // 根据收集到的姓名，精确查询本次分析涉及的办案人员信息
            List<ProsecutorProfile> affectedProfiles = profileService.findAllByNameIn(affectedProsecutorNames);
            List<Map<String, String>> finalSummary = affectedProfiles.stream()
                    .map(profile -> Map.of(
                            "prosecutorId", profile.getProsecutorId().toString(),
                            "name", profile.getName(),
                            "content", String.format("该检察官最近办理案件%d件，总问题数%d个",
                                    profile.getCaseCount(),
                                    profile.getTotalFlawCount() + profile.getTotalErrorCount())
                    ))
                    .collect(Collectors.toList());

            // 直接传递对象，让辅助方法处理JSON转换
            sendSseEvent(emitter, "complete", finalSummary);

        } catch (Exception e) {
            log.error("SSE流式分析任务顶层异常", e);
            sendSseEvent(emitter, "error", "分析任务意外终止: " + e.getMessage());
        } finally {
            emitter.complete();
            log.info("SSE流式分析任务完成，Emitter已关闭。");
        }
    }

    // 辅助方法，用于安全地发送SSE事件
    private void sendSseEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            // 1. 创建一个Map来构建统一的JSON结构
            Map<String, Object> eventData = Map.of(
                    "eventName", eventName,
                    "payload", payload
            );

            // 2. 使用 ObjectMapper 将Map转换为JSON字符串
            String jsonString = objectMapper.writeValueAsString(eventData);

            // 3. 发送事件，现在data部分永远是一个JSON字符串
            emitter.send(SseEmitter.event().name(eventName).data(jsonString));

        } catch (Exception e) {
            log.warn("发送SSE事件 '{}' 失败: {}", eventName, e.getMessage());
        }
    }

    @Override
    public Case updateDepartmentCaseNumber(String caseId, String departmentCaseNumber) {
        return caseQueryService.updateDepartmentCaseNumber(caseId, departmentCaseNumber);
    }


}