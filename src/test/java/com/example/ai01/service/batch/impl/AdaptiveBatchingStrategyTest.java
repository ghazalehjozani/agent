package com.example.ai01.service.batch.impl;

import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleScope;
import com.example.ai01.agent.model.ruleextraction.RuleSemanticMetadata;
import com.example.ai01.model.RuleCheckAssignment;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.model.RuleProjectMatch;
import com.example.ai01.service.batch.BatchingProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptiveBatchingStrategyTest {

    private AdaptiveBatchingStrategy strategy;

    @BeforeEach
    void setUp() {

        BatchingProperties properties =
                new BatchingProperties();

        properties.setMaxInputTokens(12_000);
        properties.setMaxFiles(5);
        properties.setMaxRules(10);
        properties.setMaxSegments(15);
        properties.setMaxSegmentsPerFile(3);
        properties.setMinRuleOverlap(0.25);

        strategy =
                new AdaptiveBatchingStrategy(
                        properties
                );
    }

    @Test
    void shouldMergeFilesWithCommonRulesIntoOneBatch() {

        ArchitectureRule layerRule =
                createFileRule(
                        "LAYER-001",
                        "Controllers may only depend on services"
                );

        ArchitectureRule injectionRule =
                createFileRule(
                        "DEP-002",
                        "Field injection is forbidden"
                );

        String userController =
                "/project/UserController.java";

        String orderController =
                "/project/OrderController.java";

        /*
         * هر دو فایل با هر دو Rule ارتباط دارند؛ بنابراین
         * شباهت Jaccard مجموعه Ruleهای آن‌ها برابر یک است.
         */
        List<RuleProjectMatch> matches =
                List.of(
                        createMatch(
                                layerRule,
                                userController,
                                "segment-user",
                                0.95
                        ),
                        createMatch(
                                injectionRule,
                                userController,
                                "segment-user",
                                0.91
                        ),
                        createMatch(
                                layerRule,
                                orderController,
                                "segment-order",
                                0.94
                        ),
                        createMatch(
                                injectionRule,
                                orderController,
                                "segment-order",
                                0.90
                        )
                );

        List<RuleCheckBatch> batches =
                strategy.buildBatches(matches);

        assertEquals(
                1,
                batches.size()
        );

        RuleCheckBatch batch =
                batches.get(0);

        assertTrue(
                batch.contextKey()
                        .startsWith("ADAPTIVE:")
        );

        assertEquals(
                Set.of(
                        "LAYER-001",
                        "DEP-002"
                ),
                batch.rules()
                        .stream()
                        .map(ArchitectureRule::id)
                        .collect(Collectors.toSet())
        );

        /*
         * چهار Match به دو Segment یکتا تبدیل می‌شوند.
         */
        assertEquals(
                2,
                batch.projectSegments().size()
        );

        assertEquals(
                2,
                batch.assignments().size()
        );

        RuleCheckAssignment layerAssignment =
                findAssignment(
                        batch,
                        "LAYER-001"
                );

        RuleCheckAssignment injectionAssignment =
                findAssignment(
                        batch,
                        "DEP-002"
                );

        assertEquals(
                Set.of(
                        userController,
                        orderController
                ),
                Set.copyOf(
                        layerAssignment.filePaths()
                )
        );

        assertEquals(
                Set.of(
                        userController,
                        orderController
                ),
                Set.copyOf(
                        injectionAssignment.filePaths()
                )
        );

        assertEquals(
                Set.of(
                        "segment-user",
                        "segment-order"
                ),
                Set.copyOf(
                        layerAssignment.segmentIds()
                )
        );

        assertEquals(
                Set.of(
                        "segment-user",
                        "segment-order"
                ),
                Set.copyOf(
                        injectionAssignment.segmentIds()
                )
        );
    }

    @Test
    void shouldSplitProjectRulesAcrossMultipleBatches() {

        String projectFile =
                "/project/ProjectStructure.java";

        List<RuleProjectMatch> matches =
                new ArrayList<>();

        /*
         * با maxRules=10، تعداد 25 Project Rule باید
         * به سه batch با اندازه‌های 10، 10 و 5 تقسیم شود.
         */
        IntStream.rangeClosed(1, 25)
                .forEach(index -> {

                    ArchitectureRule rule =
                            createProjectRule(
                                    "PROJECT-" + index
                            );

                    matches.add(
                            createMatch(
                                    rule,
                                    projectFile,
                                    "project-segment-" + index,
                                    1.0 - (index / 100.0)
                            )
                    );
                });

        List<RuleCheckBatch> batches =
                strategy.buildBatches(matches);

        assertEquals(
                3,
                batches.size()
        );

        assertTrue(
                batches.stream()
                        .allMatch(batch ->
                                batch.contextKey()
                                        .startsWith("PROJECT:")
                        )
        );

        /*
         * هیچ batch نباید از محدودیت Rule عبور کند.
         */
        assertTrue(
                batches.stream()
                        .allMatch(batch ->
                                batch.rules().size() <= 10
                        )
        );

        /*
         * هیچ batch نباید از محدودیت Segment عبور کند.
         */
        assertTrue(
                batches.stream()
                        .allMatch(batch ->
                                batch.projectSegments().size() <= 15
                        )
        );

        /*
         * هیچ Rule نباید هنگام تقسیم حذف شود.
         */
        Set<String> ruleIds =
                batches.stream()
                        .flatMap(batch ->
                                batch.rules().stream()
                        )
                        .map(ArchitectureRule::id)
                        .collect(Collectors.toSet());

        assertEquals(
                25,
                ruleIds.size()
        );

        /*
         * مجموع تعداد Ruleهای batchها نیز باید 25 باشد؛
         * یعنی Rule تکراری تولید نشده است.
         */
        int totalRuleCount =
                batches.stream()
                        .mapToInt(batch ->
                                batch.rules().size()
                        )
                        .sum();

        assertEquals(
                25,
                totalRuleCount
        );

        /*
         * با داده‌های این تست اندازه batchها باید دقیقاً
         * 10، 10 و 5 باشد.
         */
        List<Integer> batchSizes =
                batches.stream()
                        .map(batch ->
                                batch.rules().size()
                        )
                        .sorted()
                        .toList();

        assertEquals(
                List.of(5, 10, 10),
                batchSizes
        );
    }

    private ArchitectureRule createFileRule(
            String id,
            String description) {

        return new ArchitectureRule(
                id,
                description,
                RuleType.SEMANTIC,
                null,
                null
        );
    }

    private ArchitectureRule createProjectRule(
            String ruleId) {

        ArchitectureRule rule =
                mock(ArchitectureRule.class);

        RuleSemanticMetadata semanticMetadata =
                mock(RuleSemanticMetadata.class);

        when(rule.id())
                .thenReturn(ruleId);

        when(rule.description())
                .thenReturn(
                        "Project-wide rule " + ruleId
                );

        when(rule.ruleType())
                .thenReturn(
                        RuleType.SEMANTIC
                );

        when(rule.semanticMetadata())
                .thenReturn(
                        semanticMetadata
                );

        when(semanticMetadata.scope())
                .thenReturn(
                        RuleScope.PROJECT
                );

        return rule;
    }

    private RuleProjectMatch createMatch(
            ArchitectureRule rule,
            String filePath,
            String segmentId,
            double score) {

        Metadata metadata =
                new Metadata();

        metadata.put(
                "segmentId",
                segmentId
        );

        metadata.put(
                "segmentType",
                "CLASS"
        );

        metadata.put(
                "filePath",
                filePath
        );

        metadata.put(
                "fileName",
                fileName(filePath)
        );

        TextSegment segment =
                TextSegment.from(
                        """
                        Project Element: CLASS
                        File Path: %s
                        Segment ID: %s
                        """.formatted(
                                filePath,
                                segmentId
                        ),
                        metadata
                );

        return new RuleProjectMatch(
                rule,
                score,
                segmentId,
                "CLASS",
                filePath,
                segment
        );
    }

    private RuleCheckAssignment findAssignment(
            RuleCheckBatch batch,
            String ruleId) {

        return batch.assignments()
                .stream()
                .filter(assignment ->
                        ruleId.equals(
                                assignment.ruleId()
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Assignment not found for rule: "
                                        + ruleId
                        )
                );
    }

    private String fileName(
            String filePath) {

        int separator =
                Math.max(
                        filePath.lastIndexOf('/'),
                        filePath.lastIndexOf('\\')
                );

        return separator >= 0
                ? filePath.substring(separator + 1)
                : filePath;
    }
}