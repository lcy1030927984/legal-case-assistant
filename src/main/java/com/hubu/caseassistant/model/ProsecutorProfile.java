package com.hubu.caseassistant.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "prosecutor_profiles")
@Data
public class ProsecutorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "varchar(36)")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID prosecutorId;
    @Column(unique = true , nullable = false)
    private String name;
    private String department;
    private Integer tenure;

    // --- 请务必确认这三行代码有 '= 0' ---
    private Integer caseCount = 0;
    private Integer totalFlawCount = 0;
    private Integer totalErrorCount = 0;

    // “证据采信”
    private Integer evidenceFlawCount = 0;
    private Integer evidenceErrorCount = 0;
    // “事实认定”
    private Integer factFlawCount = 0;
    private Integer factErrorCount = 0;
    //  “法律适用”
    private Integer lawFlawCount = 0;
    private Integer lawErrorCount = 0;
    //  “办案程序合法、规范”
    private Integer procedureFlawCount = 0;
    private Integer procedureErrorCount = 0;

    @Version
    private Long version;
}