package com.hubu.caseassistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder // 使用Builder模式可以方便地构建对象
@JsonInclude(JsonInclude.Include.NON_NULL) // JSON序列化时忽略null值的字段
public class DifyChatRequest {

    private Map<String, Object> inputs;

    private String query;

    @JsonProperty("response_mode")
    private String responseMode;

    private String user;

    @JsonProperty("conversation_id")
    private String conversationId;
}