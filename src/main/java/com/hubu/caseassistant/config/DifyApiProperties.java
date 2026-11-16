package com.hubu.caseassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dify.api") // 映射 yml 中的 dify.api 前缀
@Data
public class DifyApiProperties {
    private String baseUrl;
    private String chatKey;
    private String workFlowKey;
    private String textToSqlKey;
    /** 文件上传后到触发工作流前的等待时间(毫秒)，默认3000 */
    private long workflowTriggerDelayMs = 3000;
}