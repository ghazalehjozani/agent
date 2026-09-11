package com.example.ai01.data.repository;

import com.example.ai01.data.entity.MarkdownFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarkDownFileRepository extends JpaRepository<MarkdownFileEntity, Long> {
    Optional<MarkdownFileEntity> getMarkdownFileEntityByContentHash(String hash);

    Optional<MarkdownFileEntity> findByFileName(String fileName);

}