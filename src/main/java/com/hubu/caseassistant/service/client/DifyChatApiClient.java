package com.hubu.caseassistant.service.client;

import com.hubu.caseassistant.config.DifyApiProperties;
import com.hubu.caseassistant.dto.DifyChatRequest;
import com.hubu.caseassistant.dto.DifyChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Service
@Slf4j
public class DifyChatApiClient {

    private final RestTemplate restTemplate;
    private final DifyApiProperties difyApiProperties;

    @Autowired
    public DifyChatApiClient(DifyApiProperties difyApiProperties) {
        this.restTemplate = new RestTemplate();
        this.difyApiProperties = difyApiProperties;
    }

    /**
     * 调用Dify的聊天API (阻塞模式)
     * @param prompt 用户的提问
     * @param conversationId (可选) 对话ID，用于保持上下文
     * @return Dify的响应结果
     */
    public DifyChatResponse chatCompletion(String prompt, String conversationId) {
        String url = difyApiProperties.getBaseUrl() + "/chat-messages";

        HttpHeaders headers = createHeaders();

        DifyChatRequest requestPayload = DifyChatRequest.builder()
                .inputs(Collections.emptyMap())
                .query(prompt)
                .responseMode("blocking")
                .user("java-client-example")
                .conversationId(conversationId)
                .build();

        HttpEntity<DifyChatRequest> requestEntity = new HttpEntity<>(requestPayload, headers);

        log.info("向Dify发送阻塞式聊天请求: {}", requestPayload);
        ResponseEntity<DifyChatResponse> response = restTemplate.postForEntity(url, requestEntity, DifyChatResponse.class);

        log.info("收到Dify的响应: {}", response.getBody());
        return response.getBody();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(difyApiProperties.getChatKey());
        return headers;
    }
}