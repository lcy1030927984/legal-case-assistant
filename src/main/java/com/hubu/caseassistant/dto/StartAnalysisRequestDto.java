package com.hubu.caseassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "从客户平台开始分析的请求体")
public class StartAnalysisRequestDto {

    @NotBlank(message = "bmsah (部门受案号) 不能为空")
    @Schema(description = "部门受案号", requiredMode = Schema.RequiredMode.REQUIRED, example = "鄂襄检技协受[2023]420600000067号")
    private String bmsah;

    // 如果未来有其他参数，可以在这里继续添加
    // private String otherParam;
}