package com.hubu.caseassistant.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cases")
@Data
public class Case {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String caseName; // 案件名称
    private String handlingUnit; // 承办单位
    private String handlerId; // 承办人ID (用于关联)
    private String handlerName; // 承办人姓名
    private LocalDate acceptanceDate; // 受理日期
    private String caseStatus; // 案件状态
    
    @Column(columnDefinition = "TEXT")
    private String caseSummary; // 案情摘要

    private String issueCategory; // 问题类别 (例如: 证据采信)
    private String issueType; // 问题类型 (瑕疵/错误)
    
    private String documentUrl; // 关联的电子卷宗URL或路径

    private String departmentCaseNumber; // 部门受案号
    private String transferUnit; // 移送单位
    private String transferReason; // 移送案由
    private String transferOtherReason; // 移送其他案由
    private LocalDate conclusionDate; // 审结日期
    private String conclusionSituation; // 审结情况
    private String conclusionReason; // 审结案由
    private String conclusionOtherReason; // 审结其他案由
    private String conclusionAmount; // 审结数额
    private String isPleaBargain; // 是否认罪认罚
    private String prosecutionOpinionBookNumber; // (不)起诉意见书文号
    private Integer processingDays; // 办理/审结天数
    private LocalDate firstTrialAnnouncementDate; // 一审宣告刑日期
    // 使用@Column注解，让Java字段名和数据库列名可以不同
    @Column(name = "first_trial_announcement_situation")
    private String firstTrialResult; // 一审宣告情况
    private String probationPeriod; // 缓刑时间
    private String firstTrialAnnouncementSentence; // 一审宣告刑期

    /**
     * 问题分析 Tab内容 (对应文档第四部分)
     */
    @Column(columnDefinition = "TEXT")
    private String issueAnalysisContent;

    /**
     * 问题办理情况 Tab内容 (对应文档第五部分)
     */
    @Column(columnDefinition = "TEXT")
    private String issueHandlingStatusContent;

    /**
     * 证据材料 Tab内容 (对应文档第六部分)
     */
    @Column(columnDefinition = "TEXT")
    private String evidentiaryMaterialsContent;

    @Column(columnDefinition = "TEXT")
    private String thinkingProcessReport; // 用于存储Dify的“思考过程”报告
    private String jzwjh;

    @Column(name = "supervision_point")
    private String supervisionPoint; // 监督点

    @Column(name = "problem_description", columnDefinition = "TEXT")
    private String problemDescription; // 问题描述

    @Column(name = "creation_time")
    private LocalDateTime creationTime; // 创建时间 (精确到时分秒)

}
