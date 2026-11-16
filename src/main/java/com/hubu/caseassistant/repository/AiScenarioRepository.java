package com.hubu.caseassistant.repository;

import com.hubu.caseassistant.model.AiScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiScenarioRepository extends JpaRepository<AiScenario, Long> {

    // 定义一个根据唯一标识查找场景的方法
    Optional<AiScenario> findByScenarioKey(String scenarioKey);

    // 查询所有被收藏的场景
    List<AiScenario> findByFavoriteTrue();
}