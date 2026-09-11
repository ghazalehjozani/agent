package com.example.ai01.data.repository;

import com.example.ai01.data.entity.MarkdownFileSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarkdownFileSnapShotRepository extends JpaRepository<MarkdownFileSnapshotEntity, Long> {
}
