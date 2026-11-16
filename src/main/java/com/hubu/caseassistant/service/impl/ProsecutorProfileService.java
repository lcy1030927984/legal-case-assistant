package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.model.ProsecutorProfile;
import com.hubu.caseassistant.repository.ProsecutorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProsecutorProfileService {

    private final ProsecutorProfileRepository prosecutorRepository;

    @Transactional(readOnly = true)
    public ProsecutorProfile getById(UUID prosecutorId) {
        return prosecutorRepository.findById(prosecutorId)
                .orElseThrow(() -> new EntityNotFoundException("未找到ID为: " + prosecutorId + " 的办案人员"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getAllSummaries() {
        return prosecutorRepository.findAll().stream()
                .map(profile -> Map.of(
                        "prosecutorId", profile.getProsecutorId().toString(),
                        "name", profile.getName()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 按名称查找或新建检察官画像。
     */
    public ProsecutorProfile findOrCreate(String name) {
        return prosecutorRepository.findByName(name)
                .orElseGet(() -> {
                    log.info("数据库中未找到检察官 [{}], 正在创建新记录...", name);
                    ProsecutorProfile newProfile = new ProsecutorProfile();
                    newProfile.setName(name);
                    return prosecutorRepository.saveAndFlush(newProfile);
                });
    }

    /**
     * 根据问题类别和类型更新画像的统计计数器。
     */
    public void updateIssueCounts(ProsecutorProfile profile, String category, String type) {
        if ("flaw".equalsIgnoreCase(type)) {
            profile.setTotalFlawCount(profile.getTotalFlawCount() + 1);
            switch (category) {
                case "evidence" -> profile.setEvidenceFlawCount(profile.getEvidenceFlawCount() + 1);
                case "fact"    -> profile.setFactFlawCount(profile.getFactFlawCount() + 1);
                case "law"     -> profile.setLawFlawCount(profile.getLawFlawCount() + 1);
                case "procedure" -> profile.setProcedureFlawCount(profile.getProcedureFlawCount() + 1);
            }
        } else if ("error".equalsIgnoreCase(type)) {
            profile.setTotalErrorCount(profile.getTotalErrorCount() + 1);
            switch (category) {
                case "evidence" -> profile.setEvidenceErrorCount(profile.getEvidenceErrorCount() + 1);
                case "fact"    -> profile.setFactErrorCount(profile.getFactErrorCount() + 1);
                case "law"     -> profile.setLawErrorCount(profile.getLawErrorCount() + 1);
                case "procedure" -> profile.setProcedureErrorCount(profile.getProcedureErrorCount() + 1);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ProsecutorProfile> findAllByNameIn(java.util.Set<String> names) {
        return prosecutorRepository.findAllByNameIn(names);
    }

    @Transactional
    public void save(ProsecutorProfile profile) {
        prosecutorRepository.save(profile);
    }
}
