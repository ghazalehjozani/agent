package com.example.ai01.service;

import com.example.ai01.agent.JsonReportGeneratorAgent;
import com.example.ai01.agent.RuleCheckAgent;
import com.example.ai01.agent.StructuralAgent;
import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.ExtractedResult;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ViolationFindingReport;
import com.example.ai01.configuration.PathProperties;
import com.example.ai01.tools.FileUtility;
import com.example.ai01.tools.ProjectStructureExtractor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class ArchitectureComplianceOrchestrator {
    private final StructuralAgent structuralAgent;
    private final RuleCheckAgent ruleCheckAgent;
    private JsonReportGeneratorAgent jsonReportGeneratorAgent;
    private ProjectStructureExtractor projectStructureExtractor;
    private Executor auditExecutor;
    public ArchitectureComplianceOrchestrator(StructuralAgent structuralAgent,
                                              RuleCheckAgent ruleCheckAgent,
                                              JsonReportGeneratorAgent jsonReportGeneratorAgent,
                                              ProjectStructureExtractor projectStructureExtractor,
                                              Executor auditExecutorService) {
        this.structuralAgent = structuralAgent;
        this.ruleCheckAgent = ruleCheckAgent;
        this.jsonReportGeneratorAgent = jsonReportGeneratorAgent;
        this.projectStructureExtractor = projectStructureExtractor;
        this.auditExecutor = auditExecutorService;
    }

    /**
     * @param rules
     * @param projectFiles
     * @return json structured like this
     * "standard": "SAW-101",
     * "domain": "rest-url",
     * "ruleId": "REST-URL-001",
     * "severity": "MAJOR",
     * "file": "src/main/java/com/example/ai01/controller/ComplianceController.java",
     * "line": 12,
     * "evidence": "Endpoint path is /api/compliance/analyze and does not include version segment",
     * "recommendation": "Change endpoint to /api/v1/compliance/analyses",
     * "confidence": 0.94
     * }
     */
    public ViolationFindingReport getProjectStructure(String rulesPath, String projectRoot) {

        var ruleExtractor = CompletableFuture
                .supplyAsync(() -> structuralAgent.extract(rulesPath), auditExecutor);

        var projectTreeExtractor = CompletableFuture
                .supplyAsync(() -> projectStructureExtractor.readJavaFiles(projectRoot), auditExecutor);

        var auditPipeLine = ruleExtractor
                .thenCombineAsync(projectTreeExtractor, (rules, projectTree) ->
                        ruleCheckAgent.check(new ExtractedResult(projectTree)));

        return auditPipeLine.join();
    }

    //TODO : 1. Exception Handling , 2. log intermediate results  , 3. using cache for better performance
    public ArchitectureReviewReport audit(String rulesPath, String projectRoot) {

        var ruleExtractor = CompletableFuture
                .supplyAsync(() -> structuralAgent.extract(rulesPath), auditExecutor);

        var projectTreeExtractor = CompletableFuture
                .supplyAsync(() -> projectStructureExtractor.readJavaFiles(projectRoot), auditExecutor);

        var auditPipeLine = ruleExtractor
                .thenCombineAsync(projectTreeExtractor, (rules, projectTree) ->
                        ruleCheckAgent.check(new ExtractedResult(projectTree)))
                .thenApply((violations) -> jsonReportGeneratorAgent.generateReport(violations));

        return auditPipeLine.join();

    }
}