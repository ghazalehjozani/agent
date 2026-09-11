package com.example.ai01.service;

import com.example.ai01.agent.model.RuleContainer;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.data.entity.ArchitecturalRuleEntity;
import com.example.ai01.data.entity.MarkdownFileEntity;
import com.example.ai01.data.repository.ArchitectureRuleEntityRepository;
import com.example.ai01.data.repository.MarkDownFileRepository;
import com.example.ai01.data.repository.MarkdownFileSnapShotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArchitectureRuleStorageServiceTest {

    private final MarkDownFileRepository fileRepository =
            mock(MarkDownFileRepository.class);
    private final ArchitectureRuleEntityRepository ruleRepository =
            mock(ArchitectureRuleEntityRepository.class);
    private final MarkdownFileSnapShotRepository snapshotRepository =
            mock(MarkdownFileSnapShotRepository.class);
    private final StructuralAgentExecutionService extractionService =
            mock(StructuralAgentExecutionService.class);
    private final MarkdownSplittingService splittingService =
            mock(MarkdownSplittingService.class);

    private final ArchitectureRuleStorageService service =
            new ArchitectureRuleStorageService(
                    fileRepository,
                    ruleRepository,
                    snapshotRepository,
                    extractionService,
                    splittingService
            );

    @Test
    void prefixesAndNormalizesFreshlyExtractedRuleId() {
        ArchitectureRule extracted = new ArchitectureRule(
                "REST-01",
                "A REST rule",
                RuleType.SEMANTIC,
                null,
                null
        );

        ArchitectureRule prefixed = service.prefixRuleId("01", extracted);

        assertThat(prefixed.id()).isEqualTo("01REST01");
        assertThat(prefixed.description()).isEqualTo(extracted.description());
        assertThat(prefixed.ruleType()).isEqualTo(extracted.ruleType());
        assertThat(prefixed.localMetadata()).isSameAs(extracted.localMetadata());
        assertThat(prefixed.semanticMetadata()).isSameAs(extracted.semanticMetadata());
    }

    @Test
    void cacheHitReturnsStoredIdWithoutApplyingPrefixAgain() {
        String fileName = "rest-rules.md";
        String hash = "same-hash";
        MarkdownFileEntity file = new MarkdownFileEntity(
                fileName,
                hash,
                "content",
                1L
        );
        ArchitectureRule cachedRule = new ArchitectureRule(
                "01REST01",
                "A REST rule",
                RuleType.SEMANTIC,
                null,
                null
        );
        ArchitecturalRuleEntity entity = mock(ArchitecturalRuleEntity.class);

        when(fileRepository.findByFileName(fileName))
                .thenReturn(Optional.of(file));
        when(ruleRepository.findAllByFile_FileName(fileName))
                .thenReturn(List.of(entity));
        when(entity.toDomain()).thenReturn(cachedRule);

        List<ArchitectureRule> result = service.getRules(
                "01",
                fileName,
                hash,
                "content"
        );

        assertThat(result).containsExactly(cachedRule);
        verifyNoInteractions(extractionService, splittingService);
    }
    @Test
    void prefixesFreshRulesBeforeDatabaseSave() {
        String fileName = "rest-rules.md";
        ArchitectureRule extracted = new ArchitectureRule(
                "REST-01",
                "A REST rule",
                RuleType.SEMANTIC,
                null,
                null
        );

        when(fileRepository.findByFileName(fileName))
                .thenReturn(Optional.empty());
        when(splittingService.split("content"))
                .thenReturn(List.of("chunk"));
        when(extractionService.extract("chunk"))
                .thenReturn(new RuleContainer(List.of(extracted)));
        when(fileRepository.save(any(MarkdownFileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ArchitectureRule> result = service.getRules(
                "01",
                fileName,
                "new-hash",
                "content"
        );

        assertThat(result)
                .extracting(ArchitectureRule::id)
                .containsExactly("01001REST01");

        InOrder order = inOrder(
                splittingService,
                extractionService,
                fileRepository,
                ruleRepository
        );
        order.verify(splittingService).split("content");
        order.verify(extractionService).extract("chunk");
        order.verify(fileRepository).save(any(MarkdownFileEntity.class));
        order.verify(ruleRepository).saveAll(any());
    }
}

