package com.hubu.caseassistant.repository;

import com.hubu.caseassistant.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {
    // JpaRepository 提供了所有基础的CRUD操作
    /**
     * 根据报告名称和文件哈希值检查报告是否存在
     * @param reportName 文件名
     * @param fileHash 文件内容的MD5哈希值
     * @return 如果存在则返回 true，否则返回 false
     */
    boolean existsByReportNameAndFileHash(String reportName, String fileHash);
}
