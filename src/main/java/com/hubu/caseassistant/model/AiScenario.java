package com.hubu.caseassistant.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "ai_scenarios")
@Data
public class AiScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 场景的唯一标识，用于API调用，例如 "picha_tong"
    @Column(unique = true, nullable = false)
        private String scenarioKey;

    // 场景的显示名称，例如 "援中智能-评查通"
    @Column(nullable = false)
    private String scenarioName;
    
    // 使用次数，默认为0
    @Column(nullable = false)
    private Long usageCount = 0L;
    
    // (可选) 场景描述
    private String description;

    //用于标记是否收藏
    @Column(name = "is_favorite", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean favorite = false;
    @Column(name = "icon")
    private String icon;

    /**
     * Agent 评查模式配置：该场景关联的评查项 checklist（JSON 数组字符串）。
     * 每项形如 {"code":"EV","name":"证据采信审查","category":"evidence"}。
     * 为 null 时 Agent 用默认四项 checklist（方案 §6.3：Scenario 接 Agent 模式）。
     */
    @Column(name = "checklist_config", columnDefinition = "TEXT")
    private String checklistConfig;

    /**
     * Agent 评查模式 prompt 变体（可选）。为 null 时用默认 SYSTEM_PROMPT。
     */
    @Column(name = "prompt_variant", columnDefinition = "TEXT")
    private String promptVariant;
}