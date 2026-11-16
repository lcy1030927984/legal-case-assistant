package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.config.FileStorageProperties;
import com.hubu.caseassistant.dto.CaseListItemDto;
import com.hubu.caseassistant.dto.ProblemListItemDto;
import com.hubu.caseassistant.dto.ReportDto;
import com.hubu.caseassistant.model.Case;
import com.hubu.caseassistant.repository.CaseRepository;
import com.hubu.caseassistant.repository.ReportRepository;
import com.hubu.caseassistant.service.client.SupconFeignService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaseQueryService {

    private final CaseRepository caseRepository;
    private final ReportRepository reportRepository;
    private final FileStorageProperties fileStorageProperties;
    private final SupconFeignService supconFeignService;

    @Transactional(readOnly = true)
    public List<ProblemListItemDto> getProblemListByProsecutorId(String prosecutorId) {
        List<Case> cases = caseRepository.findByHandlerId(prosecutorId);
        return cases.stream().map(c -> {
            ProblemListItemDto dto = new ProblemListItemDto();
            dto.setId(c.getId());
            dto.setDepartmentCaseNumber(c.getDepartmentCaseNumber());
            dto.setCaseName(c.getCaseName());
            dto.setSupervisionPoint(c.getSupervisionPoint());
            dto.setIssueType(c.getIssueType());
            dto.setHintType(translateIssueCategory(c.getIssueCategory()));
            dto.setProblemDescription(c.getProblemDescription());
            dto.setCreationTime(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
            dto.setCaseStatus(c.getCaseStatus());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReportDto> getAllReports() {
        return reportRepository.findAll().stream()
                .map(report -> {
                    ReportDto dto = new ReportDto();
                    dto.setReportId(report.getId());
                    dto.setReportName(report.getReportName());
                    dto.setCreationDate(report.getCreationDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CaseListItemDto> getCasesByProsecutorAndIssue(String prosecutorId, String issueCategory, String issueType) {
        List<Case> cases = caseRepository.findByHandlerIdAndIssueCategoryAndIssueType(prosecutorId, issueCategory, issueType);
        return cases.stream().map(c -> {
            CaseListItemDto dto = new CaseListItemDto();
            dto.setCaseId(c.getId());
            dto.setCaseName(c.getCaseName());
            dto.setHandlerName(c.getHandlerName());
            if (c.getAcceptanceDate() != null) {
                dto.setAcceptanceDate(c.getAcceptanceDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            dto.setIssueType(c.getIssueType());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Case getDetailsById(String caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new EntityNotFoundException("未找到ID为: " + caseId + " 的案件"));
    }

    @Transactional
    public Case updateDepartmentCaseNumber(String caseId, String departmentCaseNumber) {
        log.info("正在为案件ID [{}] 更新部门受案号为: {}", caseId, departmentCaseNumber);
        Case caseToUpdate = getDetailsById(caseId);
        caseToUpdate.setDepartmentCaseNumber(departmentCaseNumber);
        return caseRepository.save(caseToUpdate);
    }

    public Resource getCaseDocument(String caseId) throws IOException {
        Case aCase = getDetailsById(caseId);
        String filename = aCase.getDocumentUrl();

        if (filename == null || filename.isBlank()) {
            throw new IOException("该案件 (ID: " + caseId + ") 没有关联的电子卷宗文件名。");
        }

        Path filePath = Paths.get(fileStorageProperties.getStoragePath()).resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            log.info("在本地缓存中找到文件: {}", filename);
            return resource;
        }

        log.info("本地缓存未命中，正在尝试从LCBA系统下载文件: {}", filename);
        String bmsah = aCase.getDepartmentCaseNumber();
        String jzwjh = aCase.getJzwjh();

        if (bmsah == null || bmsah.isBlank() || jzwjh == null || jzwjh.isBlank()) {
            throw new IOException("无法下载文件，案件关键信息（部门受案号或卷宗文件号）缺失。");
        }

        try {
            byte[] fileContent = supconFeignService.downloadCaseDocument(bmsah, jzwjh);
            if (fileContent != null && fileContent.length > 0) {
                log.info("成功从LCBA下载文件，正在保存到本地: {}", filePath);
                Files.createDirectories(filePath.getParent());
                try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                    fos.write(fileContent);
                }
                Resource newResource = new UrlResource(filePath.toUri());
                if (newResource.exists() && newResource.isReadable()) {
                    return newResource;
                }
                throw new IOException("文件下载后保存失败，无法读取: " + filename);
            }
            throw new IOException("在LCBA系统中找到了文件记录，但文件内容为空。");
        } catch (RuntimeException e) {
            log.error("调用LCBA接口下载文件时发生运行时异常。", e);
            throw new IOException("从外部系统(LCBA)下载文件失败: " + e.getMessage(), e);
        }
    }

    private String translateIssueCategory(String category) {
        if (category == null) return "未知类型";
        switch (category) {
            case "evidence":  return "证据采信";
            case "fact":      return "事实认定";
            case "law":       return "法律适用";
            case "procedure": return "办案程序合法、合规";
            default:          return category;
        }
    }
}
