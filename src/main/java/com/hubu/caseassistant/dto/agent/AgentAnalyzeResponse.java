package com.hubu.caseassistant.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentAnalyzeResponse {
    private String answer;
    private List<AgentStepDto> steps;

    @JsonProperty("total_tokens")
    private int totalTokens;

    @JsonProperty("total_duration_ms")
    private int totalDurationMs;

    @JsonProperty("trace_id")
    private String traceId;
}
