package com.example.ai01.service;

import com.example.ai01.agent.model.ArchitectureRule;
import com.example.ai01.agent.model.RuleExtractionOutcome;
import com.example.ai01.data.entity.MarkdownFileEntity;
import com.example.ai01.data.entity.MarkdownFileSnapshotEntity;
import com.example.ai01.data.repository.ArchitectureRuleEntityRepository;
import com.example.ai01.data.repository.MarkdownFileSnapshotRepository;
import com.example.ai01.data.repository.MarkdownFileStateRepository;
import com.example.ai01.data.repository.RuleExtractionVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ArchitectureRulePersistenceService {
    private final MarkdownFileStateRepository stateRepository;
    private final RuleExtractionVersionRepository versionRepository;
    private final MarkdownFileSnapshotRepository snapshotRepository;
    private final ArchitectureRuleEntityRepository ruleRepository;

    public ArchitectureRulePersistenceService(
            MarkdownFileStateRepository stateRepository,
            RuleExtractionVersionRepository versionRepository,
            MarkdownFileSnapshotRepository snapshotRepository,
            ArchitectureRuleEntityRepository ruleRepository
    ) {
        this.stateRepository = stateRepository;
        this.versionRepository = versionRepository;
        this.snapshotRepository = snapshotRepository;
        this.ruleRepository = ruleRepository;
    }

    @Transactional(readOnly = true)
    public Object currentFileState() {
        return stateRepository.findAll().stream()
                .collect(Collectors.toMap(MarkdownFileEntity::getFileName,
                        MarkdownFileEntity::getContentHash));
    }

    @Transactional(readOnly = true)
    public RuleExtractionOutcome loadLatestVersion(boolean changed) {
        RuleExtractionVersionEntity latest = versionRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "File hashes exist but no extraction version exists"
                ));

        List<ArchitectureRule> rules = ruleRepository
                .findAllByVersion_IdOrderByRuleCodeAsc(latest.getId())
                .stream()
                .map(ArchitectureRuleEntity::toDomain)
                .toList();

        return new RuleExtractionOutcome(changed, latest.getId(), rules);
    }

    @Transactional
    public RuleExtractionOutcome saveNewVersion(
            String inputFingerprint,
            Map<String, String> currentState,
            List<MarkdownSnapshot> snapshots,
            List<ArchitectureRule> rules
    ) {
        RuleExtractionVersionEntity version = versionRepository.save(
                new RuleExtractionVersionEntity(inputFingerprint, snapshots.size())
        );

        snapshotRepository.saveAll(snapshots.stream()
                .map(snapshot -> new MarkdownFileSnapshotEntity(
                        version,
                        snapshot.,
                        snapshot.contentHash(),
                        snapshot.content()
                ))
                .toList());

        ruleRepository.saveAll(rules.stream()
                .map(rule -> new ArchitectureRuleEntity(version, rule))
                .toList());

        // Current state is replaced atomically in the same transaction.
        // Historical state remains available through markdown_file_snapshot.
        stateRepository.deleteAllInBatch();
        stateRepository.saveAll(currentState.entrySet().stream()
                .map(entry -> new MarkdownFileStateEntity(entry.getKey(), entry.getValue()))
                .toList());

        return new RuleExtractionOutcome(true, version.getId(), rules);
    }

}