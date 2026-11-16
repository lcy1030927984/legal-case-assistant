package com.hubu.caseassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.service")
@Data
public class AgentServiceProperties {
    /** Python Agent 服务地址 */
    private String url = "http://localhost:8000";
    /** 调用超时时间(秒) */
    private int timeoutSeconds = 120;
}
