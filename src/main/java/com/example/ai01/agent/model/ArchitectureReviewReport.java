package com.example.ai01.agent.model;

import java.util.List;
import java.util.Map;

public record ArchitectureReviewReport(
        String title,
        ExecutiveSummary executiveSummary,
        List<SeverityGroup> violationsBySeverity,
        List<TechnicalExplanation> technicalExplanation,
        List<SuggestedRefactoring> suggestedRefactoring,
        String conclusion
) {
    public record ExecutiveSummary(int totalViolations,
                                   Map<String, Integer> countPerSeverity,
                                   List<String> mostAffectedDomains) {
    }
    public record SeverityGroup(String severity, List<ViolationEntry> violations) {
    }
    public record ViolationEntry(String ruleId, String standard, String domain,
                                 String file, Integer line, double confidence, String evidence) {
    }
    public record TechnicalExplanation(String ruleId, String evidence, String explanation) {
    }
    public record SuggestedRefactoring(String ruleId, String fix, String codeExample) {
    }
}