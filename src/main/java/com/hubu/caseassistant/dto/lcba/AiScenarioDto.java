package com.hubu.caseassistant.dto.lcba;

import lombok.Data;

@Data
public class AiScenarioDto {
    private String scenarioKey;
    private String scenarioName;
    private Long usageCount;
    private String description;
    private boolean favorite;
    private String icon;
}