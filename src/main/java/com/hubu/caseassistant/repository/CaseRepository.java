package com.hubu.caseassistant.repository;

import com.hubu.caseassistant.model.Case;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<Case, String> {
    
    // 根据承办人ID和问题类别查询案件列表
    List<Case> findByHandlerIdAndIssueCategoryAndIssueType(String handlerId, String issueCategory, String issueType);

    boolean existsByDocumentUrl(String documentUrl);
    /**
     * 根据原始文件名查找第一条匹配的案件记录，用于缓存检查
     * @param documentUrl 原始文件名
     * @return Optional<Case>
     */
    Optional<Case> findFirstByDocumentUrl(String documentUrl);

    // 根据承办人ID查询其所有案件列表
    List<Case> findByHandlerId(String handlerId);

    // 根据问题类别和类型查询案件（跨承办人，用于类案检索）
    List<Case> findByIssueCategoryAndIssueType(String issueCategory, String issueType);

    // 根据问题类别查询案件（更宽泛的检索）
    List<Case> findByIssueCategory(String issueCategory);
}
