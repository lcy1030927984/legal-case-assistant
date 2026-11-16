package com.hubu.caseassistant.repository;

import com.hubu.caseassistant.model.ProsecutorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProsecutorProfileRepository extends JpaRepository<ProsecutorProfile, UUID> {

    /**
     * 根据办案人员姓名查找画像
     * @param name 姓名
     * @return ProsecutorProfile
     */
    Optional<ProsecutorProfile> findByName(String name);

    /**
     * 根据多个办案人员姓名查找画像列表
     * @param names 姓名集合
     * @return List<ProsecutorProfile>
     */
    List<ProsecutorProfile> findAllByNameIn(Collection<String> names);
}