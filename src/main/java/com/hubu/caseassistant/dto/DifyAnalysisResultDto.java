package com.hubu.caseassistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class DifyAnalysisResultDto {

    @JsonProperty("prosecutor_id")
    private String prosecutorId;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("department_name")
    private String departmentName;

    @JsonProperty("tenure_years")
    private Integer tenureYears;

    @JsonProperty("statistics")
    private StatisticsDto statistics;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatisticsDto {
        @JsonProperty("total_cases")
        private Integer totalCases;

        @JsonProperty("total_flaws")
        private Integer totalFlaws;

        @JsonProperty("total_errors")
        private Integer totalErrors;
    }
}