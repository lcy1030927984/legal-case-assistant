package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.dto.lcba.AiScenarioDto;
import com.hubu.caseassistant.model.AiScenario;
import com.hubu.caseassistant.repository.AiScenarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioService {

    private final AiScenarioRepository aiScenarioRepository;

    @Transactional
    public void incrementUsage(String scenarioKey) {
        log.info("正在为场景 [{}] 增加使用次数...", scenarioKey);
        aiScenarioRepository.findByScenarioKey(scenarioKey).ifPresentOrElse(
                scenario -> {
                    scenario.setUsageCount(scenario.getUsageCount() + 1);
                    aiScenarioRepository.save(scenario);
                    log.info("场景 [{}] 使用次数已增加到: {}", scenarioKey, scenario.getUsageCount());
                },
                () -> log.warn("未找到场景key为 [{}] 的记录，无法增加使用次数。", scenarioKey)
        );
    }

    @Transactional(readOnly = true)
    public List<AiScenarioDto> getAll() {
        return aiScenarioRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AiScenarioDto toggleFavorite(String scenarioKey) {
        log.info("正在为场景[{}]切换收藏状态...", scenarioKey);
        AiScenario scenario = aiScenarioRepository.findByScenarioKey(scenarioKey)
                .orElseThrow(() -> new EntityNotFoundException("未找到场景： " + scenarioKey));
        scenario.setFavorite(!scenario.isFavorite());
        AiScenario updated = aiScenarioRepository.save(scenario);
        log.info("场景 [{}] 的收藏状态已更新为: {}", scenarioKey, updated.isFavorite());
        return convertToDto(updated);
    }

    @Transactional(readOnly = true)
    public List<AiScenarioDto> getFavorites() {
        return aiScenarioRepository.findByFavoriteTrue().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private AiScenarioDto convertToDto(AiScenario scenario) {
        AiScenarioDto dto = new AiScenarioDto();
        dto.setScenarioKey(scenario.getScenarioKey());
        dto.setScenarioName(scenario.getScenarioName());
        dto.setUsageCount(scenario.getUsageCount());
        dto.setDescription(scenario.getDescription());
        dto.setFavorite(scenario.isFavorite());
        dto.setIcon(scenario.getIcon());
        return dto;
    }
}
