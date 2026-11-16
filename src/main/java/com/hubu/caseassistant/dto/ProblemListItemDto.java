// 文件: src/main/java/com/hubu/caseassistant/dto/ProblemListItemDto.java
package com.hubu.caseassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProblemListItemDto {
    private String id; // 唯一ID
    private String departmentCaseNumber; // 部门受案号
    private String caseName; // 案件名称
    private String supervisionPoint; // 监督点
    private String hintType; // 提示类型
    private String problemDescription; // 问题描述
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime creationTime; // 创建时间
    private String caseStatus; // 办理状态
    private String issueType; //问题性质（flaw/error）
}