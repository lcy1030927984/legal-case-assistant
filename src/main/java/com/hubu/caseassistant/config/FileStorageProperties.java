package com.hubu.caseassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "file") // 关联 application.yml 中 "file" 前缀的配置
@Data // 自动生成 getter 和 setter
public class FileStorageProperties {

    /**
     * 用于存储上传文件的本地目录路径.
     */
    private String storagePath;

}