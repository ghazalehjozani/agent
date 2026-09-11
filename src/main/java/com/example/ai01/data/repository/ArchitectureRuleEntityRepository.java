package com.example.ai01.data.repository;

import com.example.ai01.data.entity.ArchitecturalRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArchitectureRuleEntityRepository extends JpaRepository<ArchitecturalRuleEntity, UUID> {
    List<ArchitecturalRuleEntity> findAllByFile_FileName(String fileName);
    void deleteAllByFile_FileName(String fileName);
    List<ArchitecturalRuleEntity>
    findAllByFile_FileNameAndEnabledTrue(String fileName);
}