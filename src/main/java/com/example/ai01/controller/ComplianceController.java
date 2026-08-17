package com.example.ai01.controller;

import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.ViolationFindingReport;
import com.example.ai01.dto.AnalysisRequest;
import com.example.ai01.service.ArchitectureComplianceOrchestrator;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {
    private final ArchitectureComplianceOrchestrator orchestrator;

    public ComplianceController(ArchitectureComplianceOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/analyze")
    public ArchitectureReviewReport checkCompliance(@RequestBody AnalysisRequest request) {
        return orchestrator.audit(request.getArchitectureDoc(), request.getProjectPath());
    }

    @GetMapping("/project-structure")
    public ViolationFindingReport getProjectStructure(@RequestBody AnalysisRequest request) {
        return orchestrator.getProjectStructure(request.getArchitectureDoc(), request.getProjectPath());
    }
}