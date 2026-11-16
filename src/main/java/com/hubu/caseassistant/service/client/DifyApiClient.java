package com.hubu.caseassistant.service.client;

import com.hubu.caseassistant.config.DifyApiProperties;
import com.hubu.caseassistant.util.FileTypeUtils;
import com.hubu.caseassistant.dto.DifyChatResponse;
import com.hubu.caseassistant.dto.DifyFileUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClientException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class DifyApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final DifyApiProperties difyApiProperties;
    private final WebClient webClient;

    @Autowired
    public DifyApiClient (DifyApiProperties difyApiProperties){
        this.difyApiProperties = difyApiProperties;
        //初始化webClient
        this.webClient = WebClient.builder()
                .baseUrl(difyApiProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + difyApiProperties.getWorkFlowKey())
                .build();

    }
    /**
     * 上传文件到Dify
     */
//    public DifyFileUploadResponse uploadFile(File file) {
//        String url = difyApiProperties.getBaseUrl() + "/files/upload";
//        log.info("构造出的上传文件的url: {}",url);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setBearerAuth(difyApiProperties.getWorkFlowKey());
//
//        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//        body.add("file", new FileSystemResource(file));
//        body.add("user", "case-assistant-user");
//
//        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
//        log.info("正在上传文件 {} 到Dify...", file.getName());
//        return restTemplate.postForEntity(url, requestEntity, DifyFileUploadResponse.class).getBody();
//    }
    public DifyFileUploadResponse uploadFile(File file) {
        String url = difyApiProperties.getBaseUrl() + "/files/upload";
        log.info("构造出的上传文件的url: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(difyApiProperties.getWorkFlowKey());

        // 1. 指定文件字段自己的 Content-Type
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(FileTypeUtils.fromFilename(file.getName()));

        // 2. 用 Resource + 文件头 包装成 HttpEntity
        HttpEntity<FileSystemResource> filePart =
                new HttpEntity<>(new FileSystemResource(file), fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        // user 必须与 triggerWorkflow 的 user 一致：Dify 把 file_id 与上传者绑定，
        // 上传用 A、触发用 B 会导致 triggerWorkflow 报 "Invalid upload file"
        body.add("user", "case-assistant-system-user");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        log.info("正在上传文件 {} 到Dify... MIME: {}", file.getName(), fileHeaders.getContentType());
        return restTemplate.postForEntity(url, requestEntity, DifyFileUploadResponse.class).getBody();
    }



    /**
     * 触发Dify工作流（含自动重试，应对网络抖动和Dify文件处理延迟）
     */
    @Retryable(value = RestClientException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2))
    public String triggerWorkflow(String fileUploadId) {
        String url = difyApiProperties.getBaseUrl() + "/workflows/run";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(difyApiProperties.getWorkFlowKey());

        // --- 构建与您Dify工作流完全匹配的请求体 ---

        // 1. 创建单个文件信息对象
        Map<String, String> fileInfo = Map.of(
                "transfer_method", "local_file",
                "upload_file_id", fileUploadId,
                "type", "document"
        );

        // 2. 将这个【单个文件对象】作为 "input_file" 的值
        Map<String, Object> inputs = Map.of("input_file", fileInfo);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", inputs);
        body.put("response_mode", "blocking"); // 只使用阻塞模式
        body.put("user", "case-assistant-system-user");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        log.info("正在触发Dify工作流，请求体: {}", body);
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Dify工作流执行成功。");
            return response.getBody();
        } else {
            log.error("Dify工作流执行失败，状态码: {}, 响应体: {}", response.getStatusCode(), response.getBody());
            throw new RuntimeException("Dify工作流执行失败，状态码: " + response.getStatusCode());
        }
    }

    /*
    * 触发dify工作流（流式模式）
    * */
    public Flux<String> triggerWorkflowStreaming(String fileUploadId){
        String url = "/workflows/run";

        //构建智能体
        Map<String, String> fileInfo = Map.of(
                "transfer_method", "local_file",
                "upload_file_id", fileUploadId,
                "type", "document"
        );
        Map<String, Object> inputs = Map.of("input_file", fileInfo);
        Map<String, Object> body = new HashMap<>();
        body.put("inputs", inputs);
        body.put("response_mode", "streaming");
        body.put("user", "case-assistant-system-user");

        log.info("正在触发Dify工作流，请求体: {}", body);

        return this.webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class) // 将响应体转换为字符串流
                .doOnError(error -> log.error("Dify流式工作流执行失败", error));
    }

    /**
     * 【新增】调用Dify的Text-to-SQL应用，将自然语言问题转换为SQL
     * @param question 用户的自然语言问题
     * @return Dify生成的SQL查询字符串
     */
    public String generateSql(String question) {
        String url = difyApiProperties.getBaseUrl() + "/completion-messages";
        log.info("向Dify Text-to-SQL应用（completion模式）发送问题: {}", question);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 使用为Text-to-SQL应用专门生成的密钥
        headers.setBearerAuth(difyApiProperties.getTextToSqlKey());

        // 构建Dify应用所需的请求体
        Map<String, Object> inputs = Map.of("user_question", question);
        Map<String, Object> body = Map.of(
                "inputs", inputs,
                "query", question, // query字段通常也需要，内容可以和变量一样
                "user", "case-assistant-java-backend",
                "response_mode", "blocking" // 使用阻塞模式同步获取结果
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<DifyChatResponse> response = restTemplate.postForEntity(url, requestEntity, DifyChatResponse.class);

            if (response.getBody() != null && response.getBody().getAnswer() != null) {
                String sql = response.getBody().getAnswer().trim();
                log.info("Dify成功生成SQL: {}", sql);
                return sql;
            }
            throw new RuntimeException("Dify未能生成有效的SQL查询。");
        } catch (Exception e) {
            log.error("调用Dify Text-to-SQL应用失败", e);
            throw new RuntimeException("调用Dify Text-to-SQL应用失败: " + e.getMessage());
        }
    }
}