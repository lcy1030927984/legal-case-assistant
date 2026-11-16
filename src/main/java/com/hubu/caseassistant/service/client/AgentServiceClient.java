package com.hubu.caseassistant.service.client;

import com.hubu.caseassistant.config.AgentServiceProperties;
import com.hubu.caseassistant.dto.agent.AgentAnalyzeRequest;
import com.hubu.caseassistant.dto.agent.AgentAnalyzeResponse;
import com.hubu.caseassistant.dto.agent.AgentEvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Python Agent 服务客户端。
 *
 * <p>职责边界：只负责 HTTP 调用与反序列化，不碰数据库。Agent 产出的结构化回写由
 * {@code AgentEvaluationService} 承载（见方案 §7.2），保持本类单一职责。
 */
@Component
@Slf4j
public class AgentServiceClient {

    private final RestTemplate restTemplate;
    private final AgentServiceProperties properties;

    public AgentServiceClient(AgentServiceProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        // 应用配置的超时，避免 Python 侧长推理时 Java 线程无限挂起
        // Spring Boot 3.x 用 setConnectTimeout/setReadTimeout（旧版 connectTimeout 已移除）
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    /**
     * 调用 Python Agent 服务进行案件深度分析（旧契约：散文 answer + steps）。
     *
     * <p>保留向后兼容；新调用方应改用 {@link #evaluate(AgentAnalyzeRequest)} 拿结构化产出。
     */
    public AgentAnalyzeResponse analyze(AgentAnalyzeRequest request) {
        String url = properties.getUrl() + "/api/agent/analyze";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AgentAnalyzeRequest> httpEntity = new HttpEntity<>(request, headers);

        log.info("调用 Agent 服务分析, fileIds={}, message={}",
                request.getFileIds(), request.getMessage());

        try {
            ResponseEntity<AgentAnalyzeResponse> response = restTemplate.postForEntity(
                    url, httpEntity, AgentAnalyzeResponse.class);

            AgentAnalyzeResponse body = response.getBody();
            if (body != null) {
                log.info("Agent 分析完成, traceId={}, 耗时={}ms, 步骤数={}",
                        body.getTraceId(), body.getTotalDurationMs(),
                        body.getSteps() != null ? body.getSteps().size() : 0);
            }
            return body;
        } catch (ResourceAccessException e) {
            log.error("无法连接 Agent 服务 ({}), 请确认服务已启动: {}", url, e.getMessage());
            throw new RuntimeException("Agent 服务不可用，请确认 Python Agent 服务已启动 (" + url + ")", e);
        }
    }

    /**
     * 调用 Agent 评查员，返回结构化评查产出（方案 §6.1 契约）。
     *
     * <p>Python 侧返回的 JSON 与 {@link AgentAnalyzeResponse} 同构外加结构化字段，
     * 这里反序列化为 {@link AgentEvaluationResult}。过渡期（Agent 尚未输出 issues）
     * {@code issues} 为空，调用方据此回退到散文 {@code answer}。
     */
    public AgentEvaluationResult evaluate(AgentAnalyzeRequest request) {
        String url = properties.getUrl() + "/api/agent/analyze";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AgentAnalyzeRequest> httpEntity = new HttpEntity<>(request, headers);

        log.info("调用 Agent 评查, fileIds={}, message={}", request.getFileIds(), request.getMessage());

        try {
            ResponseEntity<AgentEvaluationResult> response = restTemplate.postForEntity(
                    url, httpEntity, AgentEvaluationResult.class);
            AgentEvaluationResult result = response.getBody();
            if (result != null) {
                int issueCount = result.getIssues() != null ? result.getIssues().size() : 0;
                log.info("Agent 评查完成, traceId={}, 耗时={}ms, 结构化问题数={}",
                        result.getTraceId(), result.getTotalDurationMs(), issueCount);
            }
            return result;
        } catch (ResourceAccessException e) {
            log.error("无法连接 Agent 服务 ({}): {}", url, e.getMessage());
            throw new RuntimeException("Agent 服务不可用，请确认 Python Agent 服务已启动 (" + url + ")", e);
        }
    }

    /**
     * 调用 Agent 服务进行对话（旧契约）。
     */
    public AgentAnalyzeResponse chat(String message) {
        String url = properties.getUrl() + "/api/agent/chat";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(body, headers);

        log.info("调用 Agent 服务对话: {}", message);

        try {
            ResponseEntity<AgentAnalyzeResponse> response = restTemplate.postForEntity(
                    url, httpEntity, AgentAnalyzeResponse.class);
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("无法连接 Agent 服务 ({}): {}", url, e.getMessage());
            throw new RuntimeException("Agent 服务不可用", e);
        }
    }

    /**
     * 健康检查：Agent 服务是否可用
     */
    public boolean isHealthy() {
        try {
            String url = properties.getUrl() + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
