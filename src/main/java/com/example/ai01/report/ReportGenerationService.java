package com.example.ai01.report;

import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.ViolationFindingReport;
import com.example.ai01.monitoring.TraceOperation;
import org.springframework.stereotype.Service;

@Service
public class ReportGenerationService {
    private final DeterministicReportBuilder reportBuilder;

    public ReportGenerationService(DeterministicReportBuilder reportBuilder) {
        this.reportBuilder = reportBuilder;
    }

    @TraceOperation(
            spanName = "report.generate",
            serviceName = "deterministic-report-builder",
            newSpan = true,
            spanKind = "CHAIN"
    )
    public ArchitectureReviewReport generate(ViolationFindingReport report) {
        return reportBuilder.build(
                report == null ? null : report.violationFindings()
        );
    }
}
