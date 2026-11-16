package com.hubu.caseassistant.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentStepDto {
    @JsonProperty("step_number")
    private int stepNumber;

    private String thought;

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("tool_input")
    private Object toolInput;

    @JsonProperty("tool_output")
    private String toolOutput;

    @JsonProperty("duration_ms")
    private int durationMs;
}
