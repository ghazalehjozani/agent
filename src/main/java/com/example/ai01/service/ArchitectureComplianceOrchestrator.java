package com.example.ai01.service;

import com.example.ai01.agent.JsonReportGeneratorAgent;
import com.example.ai01.agent.RuleCheckAgent;
import com.example.ai01.agent.StructuralAgent;
import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.ViolationFindingReport;
import com.example.ai01.configuration.PathProperties;
import com.example.ai01.tools.FileUtility;
import org.springframework.stereotype.Service;

@Service
public class ArchitectureComplianceOrchestrator {
    private final StructuralAgent structuralAgent;
    private final RuleCheckAgent ruleCheckAgent;
    private JsonReportGeneratorAgent jsonReportGeneratorAgent;
    private PathProperties pathProperties;

    public ArchitectureComplianceOrchestrator(StructuralAgent structuralAgent,
                                              RuleCheckAgent ruleCheckAgent,
                                              JsonReportGeneratorAgent jsonReportGeneratorAgent,
                                              PathProperties pathProperties) {
        this.structuralAgent = structuralAgent;
        this.ruleCheckAgent = ruleCheckAgent;
        this.jsonReportGeneratorAgent = jsonReportGeneratorAgent;
        this.pathProperties = pathProperties;
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
        var extractedResult = structuralAgent.extract(rulesPath, projectRoot);
        FileUtility.writeDownRules(extractedResult.rules(), pathProperties.getRuleResultMap());
        return ruleCheckAgent.check(extractedResult);
    }

    //TODO : 1. Exception Handling , 2. log intermediate results  , 3. using cache for better performance
    public ArchitectureReviewReport audit(String rulesPath, String projectRoot) {
        var structuralData = structuralAgent.extract(rulesPath, projectRoot);
        FileUtility.writeDownRules(structuralData.rules(), pathProperties.getRuleResultMap());
        var violations = ruleCheckAgent.check(structuralData);
        return jsonReportGeneratorAgent.generateReport(violations);

    }

}