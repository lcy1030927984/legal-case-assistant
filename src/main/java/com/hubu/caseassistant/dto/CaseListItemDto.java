package com.hubu.caseassistant.dto;

import lombok.Data;

@Data
public class CaseListItemDto {
    private String caseId;
    private String caseName;
    private String handlerName;// 承办人姓名
    private String acceptanceDate;// 受理日期
    private String issueType; // 瑕疵或错误
}
