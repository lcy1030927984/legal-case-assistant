package com.hubu.caseassistant.dto;

import lombok.Data;
import java.util.List;

@Data
public class StartAnalysisRequest {
    private List<String> reportIds;
}
