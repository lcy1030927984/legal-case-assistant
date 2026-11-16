package com.hubu.caseassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("人工智能辅助案件质量监控管理系统 API")
                        .version("1.0.0")
                        .description("本文档为案件辅助系统后端提供给前端的API接口文档")
                        .termsOfService("http://doc.hubu.com")
                        .contact(new Contact().name("后端开发").url("http://www.hubu.com").email("dev@yourcompany.com"))
                        .license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0.html"))
                );
    }
}