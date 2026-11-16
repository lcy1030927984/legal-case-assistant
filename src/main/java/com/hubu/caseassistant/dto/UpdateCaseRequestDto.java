// src/main/java/com/hubu/caseassistant/dto/UpdateCaseRequestDto.java
package com.hubu.caseassistant.dto;

import lombok.Data;

@Data
public class UpdateCaseRequestDto {
    private String departmentCaseNumber;
    // 如果未来还想修改其他字段，可以在这里添加
}