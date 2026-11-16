package com.hubu.caseassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubu.caseassistant.dto.*;
import com.hubu.caseassistant.dto.lcba.AiScenarioDto;
import com.hubu.caseassistant.model.Case;
import com.hubu.caseassistant.model.ProsecutorProfile;
import com.hubu.caseassistant.service.CaseAnalysisService;
import com.hubu.caseassistant.service.DataQueryService;
import com.hubu.caseassistant.service.client.AgentServiceClient;
import com.hubu.caseassistant.service.client.SupconFeignService;
import com.hubu.caseassistant.dto.agent.AgentAnalyzeRequest;
import com.hubu.caseassistant.dto.agent.AgentAnalyzeResponse;
import com.hubu.caseassistant.repository.CaseRepository;
import com.hubu.caseassistant.util.FileTypeUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api")
@Slf4j
@Tag(name = "案件分析核心接口")
@RequiredArgsConstructor
public class CaseAnalysisController {

    private final CaseAnalysisService caseAnalysisService;
    private final SupconFeignService supconFeignService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataQueryService dataQueryService;
    private final AgentServiceClient agentServiceClient;
    private final CaseRepository caseRepository;
    private final com.hubu.caseassistant.service.impl.AgentEvaluationService agentEvaluationService;

    private final ThreadPoolExecutor sseExecutor = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "sse-" + threadCounter.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    @PreDestroy
    public void shutdown() {
        sseExecutor.shutdown();
        try {
            if (!sseExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                sseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @GetMapping("/reports")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取质量评估报告列表")
    public ResponseEntity<ApiResponse<List<ReportDto>>> getReports() {
        List<ReportDto> reportList = caseAnalysisService.getAllReports();
        return ResponseEntity.ok(ApiResponse.success(reportList));
    }

    @PostMapping("/analysis/start")
    @CrossOrigin(origins = "*")
    @Operation(summary = "上传文件并开始分析任务")
    public ResponseEntity<ApiResponse<StartAnalysisResponse>> startAnalysis(@RequestParam("files") MultipartFile[] files) {
        String analysisId = caseAnalysisService.startAnalysis(files);
        StartAnalysisResponse data = new StartAnalysisResponse();
        data.setAnalysisId(analysisId);
        return new ResponseEntity<>(ApiResponse.accepted(data), HttpStatus.ACCEPTED);
    }

    /**
     * 根据客户平台的部门受案号，触发文件拉取和分析任务
     */
    @PostMapping("/analysis/from-client-platform")
    @CrossOrigin(origins = "*")
    @Operation(summary = "从客户平台根据案号拉取文件并开始分析")
    public ResponseEntity<ApiResponse<String>> startAnalysisFromClientPlatform(@Valid @RequestBody StartAnalysisRequestDto request) {
        String bmsah = request.getBmsah();
        if (bmsah == null || bmsah.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "bmsah (部门受案号) 不能为空"));
        }

        // 异步执行任务，立即返回202 Accepted
        // 如果任务执行时间很长，建议使用异步，否则可以直接调用
        CompletableFuture.runAsync(() -> {
            caseAnalysisService.analyzeCaseFromClientPlatform(bmsah);
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted("任务已接受，正在后台处理中"));
    }


    @GetMapping("/prosecutors/summary")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取分析结果中所有办案人列表")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getAllProsecutors(/*@PathVariable String analysisId*/) {
        caseAnalysisService.incrementScenarioUsage("pingcha_tong");

        List<Map<String, String>> prosecutorList = caseAnalysisService.getAllProsecutorsSummary();
        return ResponseEntity.ok(ApiResponse.success(prosecutorList));
    }

    @GetMapping("/prosecutors/{prosecutorId}/profile")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取指定办案人员的完整画像信息")
    public ResponseEntity<ApiResponse<ProsecutorProfile>> getProsecutorProfile(@PathVariable String prosecutorId) {

        // 在调用service之前，手动将String转换为UUID ---
        UUID prosecutorUuid = UUID.fromString(prosecutorId);

        // 使用UUID类型来调用service方法
        ProsecutorProfile profile = caseAnalysisService.getProsecutorProfileById(prosecutorUuid);

        return ResponseEntity.ok(ApiResponse.success(profile));
    }



    @GetMapping("/prosecutors/{prosecutorId}/issues/{issueCategory}/{issueType}")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取指定办案人特定类别问题的案件列表")
    public ResponseEntity<ApiResponse<List<CaseListItemDto>>> getCasesByIssue(
            @PathVariable String prosecutorId,
            @PathVariable String issueCategory,
            @PathVariable String issueType) {
        List<CaseListItemDto> cases = caseAnalysisService.getCasesByProsecutorAndIssueCategoryAndIssueType(prosecutorId, issueCategory, issueType);
        return ResponseEntity.ok(ApiResponse.success(cases));
    }

    @GetMapping("/cases/{caseId}")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取单个案件的详细信息")
    public ResponseEntity<ApiResponse<Case>> getCaseDetails(@PathVariable String caseId) {
        Case caseDetails = caseAnalysisService.getCaseDetailsById(caseId);
        return ResponseEntity.ok(ApiResponse.success(caseDetails));
    }

    @GetMapping("/cases/{caseId}/document")
    @CrossOrigin(origins = "*")
    @Operation(summary = "下载案件关联的电子卷宗")
    public ResponseEntity<Resource> downloadCaseDocument(@PathVariable String caseId) {
        try {
            Resource resource = caseAnalysisService.getCaseDocument(caseId);
            String filename = resource.getFilename();

            if (filename == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            MediaType mediaType = FileTypeUtils.fromFilename(filename);

            // 对文件名进行URL编码，以支持中文等特殊字符
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");


            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename)
                    .body(resource);

        } catch (IOException e) {
            log.error("下载文件失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 根据已上传的报告ID列表开始分析任务
     *
     * @param request 包含报告ID列表的请求体
     * @return 返回分析后生成的检察官简报列表
     */
    @PostMapping("/analysis/start-from-reports")
    @CrossOrigin(origins = "*")
    @Operation(summary = "根据报告ID开始分析并获取检察官简报")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> startAnalysisFromReports(@RequestBody StartAnalysisRequest request) {
        caseAnalysisService.incrementScenarioUsage("pingcha_tong");

        List<Map<String, String>> prosecutorsSummary = caseAnalysisService.startAnalysisFromReports(request.getReportIds());
        return ResponseEntity.ok(ApiResponse.success(prosecutorsSummary));
    }

    /**
     * 根据已上传的报告ID列表开始流式分析任务
     *
     * @param reportIds 包含报告ID列表的请求体
     * @return SseEmitter 用于向客户端流式传输数据
     */
    @GetMapping(value = "/analysis/stream-from-reports", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin(origins = "*")
    @Operation(summary = "根据报告ID开始流式分析并获取实时过程")
    public SseEmitter startStreamAnalysisFromReports(@RequestParam(value = "reportIds", required = false) List<String> reportIds) {
        caseAnalysisService.incrementScenarioUsage("pingcha_tong");

        // 创建一个SseEmitter实例，设置为较长的超时时间
        SseEmitter emitter = new SseEmitter(1800_000L);
        // 使用已有的线程池来异步执行耗时任务，避免阻塞Tomcat线程
        sseExecutor.execute(() -> {
            try {
                caseAnalysisService.startStreamingAnalysis(reportIds, emitter);
            } catch (Exception e) {
                log.error("SSE流式分析任务发生错误: ", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping("/scenarios")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取所有AI场景的列表")
    public ResponseEntity<ApiResponse<List<AiScenarioDto>>> getAiScenarios() {
        List<AiScenarioDto> scenarios = caseAnalysisService.getAllScenarios();
        return ResponseEntity.ok(ApiResponse.success(scenarios));
    }

//    @GetMapping("/prosecutors/{prosecutorId}/cases")
//    @CrossOrigin(origins = "*")
//    @Operation(summary = "获取指定检察官的所有案件列表")
//    public ResponseEntity<ApiResponse<List<CaseListItemDto>>> getAllCasesByProsecutorId(@PathVariable String prosecutorId){
//        List<CaseListItemDto> cases = caseAnalysisService.getAllCasesByProsecutorId(prosecutorId);
//        return ResponseEntity.ok(ApiResponse.success(cases));
//    }

    @GetMapping("/prosecutors/{prosecutorId}/problems")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取指定办案人的问题列表")
    public ResponseEntity<ApiResponse<List<ProblemListItemDto>>> getProblemList(@PathVariable String prosecutorId) {
        List<ProblemListItemDto> problems = caseAnalysisService.getProblemListByProsecutorId(prosecutorId);
        return ResponseEntity.ok(ApiResponse.success(problems));
    }

    /**
     * 【新增】切换场景收藏状态的接口
     */
    @PostMapping("/scenarios/{scenarioKey}/toggle-favorite")
    @CrossOrigin(origins = "*")
    @Operation(summary = "切换AI场景的收藏状态")
    public ResponseEntity<ApiResponse<AiScenarioDto>> toggleScenarioFavorite(@PathVariable String scenarioKey) {
        AiScenarioDto updatedScenario = caseAnalysisService.toggleFavorite(scenarioKey);
        return ResponseEntity.ok(ApiResponse.success(updatedScenario));
    }

    /**
     * 【新增】获取收藏场景列表的接口
     */
    @GetMapping("/scenarios/favorites")
    @CrossOrigin(origins = "*")
    @Operation(summary = "获取所有已收藏的AI场景")
    public ResponseEntity<ApiResponse<List<AiScenarioDto>>> getFavoriteScenarios() {
        List<AiScenarioDto> scenarios = caseAnalysisService.getFavoriteScenarios();
        return ResponseEntity.ok(ApiResponse.success(scenarios));
    }

    /**
     * 【新增】更新指定案件的信息（目前用于更新部门受案号）
     */
    @PutMapping("/cases/{caseId}")
    @CrossOrigin(origins = "*")
    @Operation(summary = "更新单个案件的信息")
    public ResponseEntity<ApiResponse<Case>> updateCaseDetails(
            @PathVariable String caseId,
            @RequestBody UpdateCaseRequestDto request) {

        Case updatedCase = caseAnalysisService.updateDepartmentCaseNumber(caseId, request.getDepartmentCaseNumber());
        return ResponseEntity.ok(ApiResponse.success(updatedCase));
    }

    /**
     * 【新增】接收自然语言问题并返回统计结果
     */
    @PostMapping("/data-query/ask")
    @CrossOrigin(origins = "*")
    @Operation(summary = "自然语言数据查询")
    public ResponseEntity<ApiResponse<String>> askDataQuery(@RequestBody DataQueryRequestDto request) {
        String answer = dataQueryService.answerQuestion(request.getQuestion());
        return ResponseEntity.ok(ApiResponse.success(answer));
    }

    // ==================== 类案查询接口 ====================

    @GetMapping("/cases/search")
    @CrossOrigin(origins = "*")
    @Operation(summary = "根据问题类别和类型查询类案（供 Agent 服务调用）")
    public ResponseEntity<ApiResponse<List<Case>>> searchSimilarCases(
            @RequestParam String issueCategory,
            @RequestParam(required = false) String issueType,
            @RequestParam(defaultValue = "10") int limit) {
        List<Case> cases;
        if (issueType != null && !issueType.isBlank()) {
            cases = caseRepository.findByIssueCategoryAndIssueType(issueCategory, issueType);
        } else {
            cases = caseRepository.findByIssueCategory(issueCategory);
        }
        // 限制返回数量
        if (cases.size() > limit) {
            cases = cases.subList(0, limit);
        }
        return ResponseEntity.ok(ApiResponse.success(cases));
    }

    // ==================== Agent 智能分析接口 ====================

    @PostMapping("/agent/analyze")
    @CrossOrigin(origins = "*")
    @Operation(summary = "Agent 智能分析法律文书（调用 Python Agent 服务）")
    public ResponseEntity<ApiResponse<AgentAnalyzeResponse>> agentAnalyze(
            @RequestBody AgentAnalyzeRequest request) {
        try {
            AgentAnalyzeResponse result = agentServiceClient.analyze(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            log.error("Agent 分析失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(503, e.getMessage()));
        }
    }

    @PostMapping("/agent/chat")
    @CrossOrigin(origins = "*")
    @Operation(summary = "Agent 智能对话（调用 Python Agent 服务）")
    public ResponseEntity<ApiResponse<AgentAnalyzeResponse>> agentChat(
            @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String caseId = request.get("caseId");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "message 不能为空"));
        }
        try {
            // 方案 §6.2：若带 caseId，把该案已落库的结构化评查结果作为上下文拼进消息，
            // 让 Agent 不必重读卷宗即可“就本案追问”。无评查记录则按原样转发（即时评查留 P2）。
            String enrichedMessage = agentEvaluationService.enrichMessageWithContext(caseId, message);
            AgentAnalyzeResponse result = agentServiceClient.chat(enrichedMessage);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            log.error("Agent 对话失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(503, e.getMessage()));
        }
    }

    @GetMapping("/agent/health")
    @CrossOrigin(origins = "*")
    @Operation(summary = "检查 Agent 服务健康状态")
    public ResponseEntity<ApiResponse<Map<String, Object>>> agentHealth() {
        boolean healthy = agentServiceClient.isHealthy();
        Map<String, Object> info = new java.util.HashMap<>();
        info.put("agent_service_available", healthy);
        info.put("status", healthy ? "ok" : "unavailable");
        if (healthy) {
            return ResponseEntity.ok(ApiResponse.success(info));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "Agent 服务不可用"));
    }

}
