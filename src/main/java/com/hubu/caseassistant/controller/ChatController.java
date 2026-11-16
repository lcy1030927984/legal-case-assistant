package com.hubu.caseassistant.controller;

import com.hubu.caseassistant.dto.ApiResponse;
import com.hubu.caseassistant.dto.DifyChatResponse;
import com.hubu.caseassistant.service.client.DifyChatApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "智能问答接口 (Chat)")
@Slf4j
public class ChatController {

    private final DifyChatApiClient difyChatApiClient;

    @Autowired
    public ChatController(DifyChatApiClient difyChatApiClient) {
        this.difyChatApiClient = difyChatApiClient;
    }

    @PostMapping("/send")
    @Operation(summary = "发送聊天消息")
    @CrossOrigin(origins = "*")
    public ResponseEntity<ApiResponse<DifyChatResponse>> sendMessage(
            @RequestBody Map<String, String> payload) {

        String prompt = payload.get("prompt");
        String conversationId = payload.get("conversationId");

        log.info("接收到聊天消息请求：prompt='{}', conversationId='{}'", prompt, conversationId);

        if (prompt == null || prompt.isEmpty()) {
            log.warn("请求中prompt为空");
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "prompt不能为空"));
        }

        try {
            DifyChatResponse difyResponse = difyChatApiClient.chatCompletion(prompt, conversationId);
            log.info("Dify API 调用成功，收到响应：{}", difyResponse);
            return ResponseEntity.ok(ApiResponse.success(difyResponse));
        } catch (RestClientException e) {
            log.error("调用Dify API 失败，RestTemplate 异常：", e);
            return ResponseEntity.status(502).body(ApiResponse.error(502, "与Dify服务通信失败：" + e.getMessage()));
        } catch (Exception e) {
            log.error("处理聊天消息时发生未知错误：", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "服务器内部错误"));
        }
    }
}