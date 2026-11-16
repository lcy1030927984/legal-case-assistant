package com.hubu.caseassistant.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String reportName;

    @Column(nullable = false)
    private LocalDateTime creationDate;

    /**
     * --- 在此处添加新字段 ---
     * 用于存储上传到Dify后返回的文件ID。这是必需的，以便后续可以根据报告ID触发分析。
     */
    @Column(nullable = false)
    private String fileId;

    @Column(name = "file_hash", length = 32)
    private String fileHash;

    // 【新增字段】用于存储文件在磁盘上的唯一名称 (例如: 1678886400000_案件报告.pdf)
    @Column(unique = true) // 确保这个文件名是唯一的
    private String uniqueFilenameOnDisk;
}