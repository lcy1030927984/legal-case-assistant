package com.hubu.caseassistant;

import com.hubu.caseassistant.config.DifyApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableRetry
@EnableConfigurationProperties(DifyApiProperties.class) // 在这里添加注解，明确启用 DifyApiProperties
public class CaseAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaseAssistantApplication.class, args);
    }
}
