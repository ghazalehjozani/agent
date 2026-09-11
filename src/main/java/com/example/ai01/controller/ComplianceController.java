package com.example.ai01.controller;

import com.example.ai01.agent.model.ArchitectureReviewReport;
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

    /*

    final flow :

                         RULES
                       │
                       ▼
                 Rule Embeddings
                       │
                       │
      ┌────────────────┼────────────────┐
      │                │                │
      ▼                ▼                ▼
   Rule-1           Rule-2           Rule-N
      │                │                │
      └──────── parallel PGVector ──────┘
                       │
                       ▼
              Project Vector Store
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       CLASS       DEPENDENCY     METHOD
                       │
                       ▼
              RuleProjectMatch
                       │
           ┌───────────┴───────────┐
           │                       │
           ▼                       ▼
      no matches                 matches
           │                       │
           ▼                       ▼
        REJECT               group by context
                                   │
                   ┌───────────────┼───────────────┐
                   ▼               ▼               ▼
             Controller.java  Service.java    Repository.java
                   │               │               │
               Rules[]          Rules[]          Rules[]
                   │               │               │
                   └──── RuleCheckAgents ──────────┘
                              parallel
                                  │
                                  ▼
                          Compliance Results

     */

    @PostMapping("/analyze")
    public ArchitectureReviewReport checkCompliance(@RequestBody AnalysisRequest request) {
        return orchestrator.audit(request.getArchitectureDoc(), request.getProjectPath());
    }

}