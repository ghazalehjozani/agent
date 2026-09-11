package com.example.ai01.service;

import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ViolationFindingReport;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.model.EmbeddingStoreRequest;
import com.example.ai01.model.ClassRuleCheckBatch;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.monitoring.TraceOperation;
import com.example.ai01.service.deterministic.RuleStrategyRegistry;
import com.example.ai01.report.ReportGenerationService;
import io.opentelemetry.context.Context;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

@Service
public class ArchitectureComplianceOrchestrator {

    private final RuleCheckExecutionService ruleCheckExecutionService;
    private final ReportGenerationService reportGenerationService;
    private final Executor auditExecutor;
    private final Executor agentExecutor;
    private final Environment environment;
    private final RuleExtractionService ruleExtractionService;
    private final EmbeddingFacadeService embeddingFacadeService;
    private final JavaStructureService javaStructureService;
    private final ProjectClassContextAssembler projectClassContextAssembler;
    private final RuleStrategyRegistry ruleStrategyRegistry;

    public ArchitectureComplianceOrchestrator(
            RuleCheckExecutionService ruleCheckExecutionService,
            ReportGenerationService reportGenerationService,
            @Qualifier("auditExecutor")
            Executor auditExecutor,
            @Qualifier("agentExecutor")
            Executor agentExecutor,
            Environment environment,
            RuleExtractionService ruleExtractionService,
            EmbeddingFacadeService embeddingFacadeService,
            JavaStructureService javaStructureService,
            ProjectClassContextAssembler projectClassContextAssembler,
            RuleStrategyRegistry ruleStrategyRegistry) {

        this.ruleCheckExecutionService =
                ruleCheckExecutionService;

        this.reportGenerationService =
                reportGenerationService;

        this.auditExecutor =
                auditExecutor;

        this.agentExecutor =
                agentExecutor;

        this.environment =
                environment;

        this.ruleExtractionService =
                ruleExtractionService;

        this.embeddingFacadeService =
                embeddingFacadeService;

        this.javaStructureService = javaStructureService;

        this.projectClassContextAssembler = projectClassContextAssembler;
        this.ruleStrategyRegistry = ruleStrategyRegistry;
    }

    @TraceOperation(
            spanName = "architecture.audit",
            serviceName = "architecture-orchestrator",
            newSpan = true,
            spanKind = "CHAIN"
    )
    public ArchitectureReviewReport audit(
            List<String> rulesPath,
            String projectRoot) {

        Context auditContext =
                Context.current();

        /**
         *
         * this line will prepare
         * OpenTelemetry for context exchange
         */
        Executor requestExecutor =
                command -> auditExecutor.execute(
                        auditContext.wrap(command)
                );

        /**
         * this line will prepare
         * OpenTelemetry for context exchange
         */
        Executor requestAgentExecutor =
                command -> agentExecutor.execute(
                        auditContext.wrap(command)
                );

        /*
         * استخراج Ruleها و Project Tree به‌صورت موازی.
         */
        CompletableFuture<List<ArchitectureRule>> rulesFuture = ruleExtractionService.extractRules(rulesPath);
        var projectTreeFuture = javaStructureService.extract(projectRoot);

        CompletableFuture<ArchitectureReviewReport> auditPipeline =
                rulesFuture.thenCombineAsync(
                                projectTreeFuture,
                                (rules, projectTree) -> {

                                    List<ArchitectureRule> internalRules =
                                            rules.stream()
                                                    .filter(rule -> rule.ruleType() == RuleType.DETERMINISTIC)
                                                    .filter(rule -> !requiresLlmCheck(rule))
                                                    .filter(ruleStrategyRegistry::supports)
                                                    .toList();

                                    List<ArchitectureRule> semanticRules =
                                            rules.stream()
                                                    .filter(this::requiresLlmCheck)
                                                    .toList();

                                    CompletableFuture<List<ViolationFinding>> internalFindingsFuture =
                                            CompletableFuture.supplyAsync(
                                                    () -> internalRules.stream()
                                                            .flatMap(rule -> ruleStrategyRegistry.evaluate(rule, projectTree).stream())
                                                            .toList(),
                                                    requestExecutor
                                            );

                                    UUID projectId =
                                            environment.getRequiredProperty(
                                                    "app.project.id",
                                                    UUID.class
                                            );

                                    /**
                                     *
                                     * store all
                                     * embeddings into pgvector
                                     */
                                    var embeddedRules =
                                            embeddingFacadeService.store(
                                                    new EmbeddingStoreRequest(
                                                            projectId,
                                                            projectTree,
                                                            rules
                                                    )
                                            );

                                    /**
                                     * this will get us
                                     * batch structure for parallel execution
                                     * on rule-check agent
                                     */
                                    List<RuleCheckBatch> retrievalBatches =
                                            embeddingFacadeService.search(
                                                    projectId.toString(),
                                                    semanticRules,
                                                    embeddedRules
                                            );

                                    /*
                                     * Segmentها فقط برای retrieval استفاده شدند.
                                     * ورودی Agent از این نقطه به بعد شامل نمای
                                     * کامل و فشرده JavaFileها است.
                                     */
                                    List<ClassRuleCheckBatch> batches =
                                            projectClassContextAssembler.assemble(
                                                    projectTree,
                                                    retrievalBatches
                                            );

                                    /*
                                     * اجرای موازی batchها روی agentExecutor.
                                     */
                                    List<CompletableFuture<ViolationFindingReport>>
                                            checkFutures =
                                            batches.stream()
                                                    .map(batch ->
                                                            CompletableFuture.supplyAsync(
                                                                    () -> executeRuleCheckBatch(
                                                                            batch
                                                                    ),
                                                                    requestAgentExecutor
                                                            )
                                                    )
                                                    .toList();

                                    /*
                                     * انتظار برای تکمیل همه Agentها.
                                     */
                                    CompletableFuture.allOf(
                                            checkFutures.toArray(
                                                    CompletableFuture[]::new
                                            )
                                    ).join();

                                    /*
                                     * تجمیع یافته‌های تمام batchها.
                                     */
                                    List<ViolationFinding> findings =
                                            Stream.concat(
                                                    internalFindingsFuture.join().stream(),
                                                    checkFutures.stream()
                                                    .map(CompletableFuture::join)
                                                    .filter(Objects::nonNull)
                                                    .flatMap(report -> {

                                                        if (report.violationFindings()
                                                                == null) {

                                                            return Stream
                                                                    .<ViolationFinding>empty();
                                                        }

                                                        return report
                                                                .violationFindings()
                                                                .stream();
                                                    }))
                                                    .toList();

                                    return new ViolationFindingReport(
                                            findings
                                    );
                                },
                                requestExecutor
                        )
                        /*
                         * تولید گزارش با Context همان درخواست.
                         */
                        .thenApplyAsync(
                                reportGenerationService::generate,
                                requestExecutor
                        );

        return auditPipeline.join();
    }

    private ViolationFindingReport executeRuleCheckBatch(
            ClassRuleCheckBatch batch) {

        return ruleCheckExecutionService.check(
                batch
        );
    }

    private boolean requiresLlmCheck(
            ArchitectureRule rule) {

        if (rule.ruleType() == RuleType.SEMANTIC) {
            return true;
        }

        if (rule.ruleType() != RuleType.DETERMINISTIC) {
            return false;
        }

        return rule.localMetadata() == null
                || rule.localMetadata().strategyKey() == null
                || rule.localMetadata().strategyKey().isBlank();
    }
}