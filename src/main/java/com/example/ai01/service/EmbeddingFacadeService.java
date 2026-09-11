package com.example.ai01.service;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.vector.EmbeddedRule;
import com.example.ai01.model.EmbeddingStoreRequest;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.model.RuleProjectMatch;
import com.example.ai01.monitoring.TraceOperation;
import com.example.ai01.service.batch.RuleBatchingStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmbeddingFacadeService {

    private static final double MIN_SCORE = 0.55;
    private static final int MAX_MATCHES_PER_RULE = 20;

    private final RulesEmbeddingService rulesEmbeddingService;
    private final ProjectEmbeddingService projectEmbeddingService;
    private final ExecutorService matchingExecutor;
    private final RuleBatchingStrategy ruleBatchingStrategy;

    public EmbeddingFacadeService(
            RulesEmbeddingService rulesEmbeddingService,
            ProjectEmbeddingService projectEmbeddingService,
            @Qualifier("matchingExecutor") ExecutorService matchingExecutor,
            RuleBatchingStrategy ruleBatchingStrategy) {

        this.rulesEmbeddingService = rulesEmbeddingService;
        this.projectEmbeddingService = projectEmbeddingService;
        this.matchingExecutor = matchingExecutor;
        this.ruleBatchingStrategy = ruleBatchingStrategy;
    }

    @TraceOperation(
            serviceName = "embedding-facade",
            spanName = "embedding.store",
            newSpan = true
    )
    public List<EmbeddedRule> store(EmbeddingStoreRequest request) {
        return projectEmbeddingService
                .storeAsync(
                        request.projectId().toString(),
                        request.projectRoot()
                )
                .thenCombineAsync(
                        rulesEmbeddingService.storeAsync(request.rules()),
                        (ignored, embeddableRules) -> embeddableRules,
                        matchingExecutor
                )
                .join();
    }

    @TraceOperation(
            serviceName = "embedding-facade",
            spanName = "embedding.search",
            newSpan = true
    )
    public List<RuleCheckBatch> search(
            String projectId,
            List<ArchitectureRule> rules,
            List<EmbeddedRule> embeddedRules) {

        if (rules == null
                || rules.isEmpty()
                || embeddedRules == null
                || embeddedRules.isEmpty()) {
            return List.of();
        }

        Map<String, ArchitectureRule> ruleById = rules.stream()
                .collect(Collectors.toMap(
                        ArchitectureRule::id,
                        Function.identity(),
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new
                ));

        List<CompletableFuture<List<RuleProjectMatch>>> futures =
                embeddedRules.stream()
                        .map(embeddedRule ->
                                CompletableFuture.supplyAsync(
                                        () -> searchRule(
                                                projectId,
                                                ruleById,
                                                embeddedRule
                                        ),
                                        matchingExecutor
                                )
                        )
                        .toList();

        List<RuleProjectMatch> segmentMatches = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Collection::stream)
                .toList();

        if (segmentMatches.isEmpty()) {
            return List.of();
        }

        /*
         * PGVector may return CLASS, METHOD and DEPENDENCY segments belonging
         * to the same Java file. Keep only the strongest match per Rule/File;
         * the complete JavaFile will be hydrated later by the assembler.
         */
        List<RuleProjectMatch> fileMatches =
                keepBestMatchPerRuleAndFile(segmentMatches);

        return ruleBatchingStrategy.buildBatches(fileMatches);
    }

    private List<RuleProjectMatch> searchRule(
            String projectId,
            Map<String, ArchitectureRule> ruleById,
            EmbeddedRule embeddedRule) {

        ArchitectureRule rule = ruleById.get(embeddedRule.ruleId());
        if (rule == null) {
            return List.of();
        }

        return projectEmbeddingService.search(
                projectId,
                rule,
                embeddedRule,
                MIN_SCORE,
                MAX_MATCHES_PER_RULE
        );
    }

    private List<RuleProjectMatch> keepBestMatchPerRuleAndFile(
            List<RuleProjectMatch> matches) {

        Map<String, RuleProjectMatch> bestByRuleAndFile =
                matches.stream()
                        .filter(Objects::nonNull)
                        .filter(match -> match.rule() != null)
                        .collect(Collectors.toMap(
                                this::ruleFileKey,
                                Function.identity(),
                                (first, second) ->
                                        first.score() >= second.score()
                                                ? first
                                                : second,
                                LinkedHashMap::new
                        ));

        return bestByRuleAndFile.values().stream()
                .sorted(Comparator.comparingDouble(
                        RuleProjectMatch::score
                ).reversed())
                .toList();
    }

    private String ruleFileKey(RuleProjectMatch match) {
        String fileKey = match.filePath();

        if (fileKey == null || fileKey.isBlank()) {
            fileKey = match.segmentId();
        }

        return match.rule().id()
                + "\u0000"
                + Objects.toString(fileKey, "");
    }
}
