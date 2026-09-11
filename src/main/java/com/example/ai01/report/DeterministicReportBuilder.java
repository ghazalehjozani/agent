package com.example.ai01.report;

import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.ViolationFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DeterministicReportBuilder {
    private static final List<String> SEVERITY_ORDER =
            SeverityPolicy.ORDER;

    private final ViolationDeduplicator deduplicator;

    public DeterministicReportBuilder(ViolationDeduplicator deduplicator) {
        this.deduplicator = deduplicator;
    }

    public ArchitectureReviewReport build(List<ViolationFinding> rawFindings) {
        List<ViolationFinding> findings = deduplicator.deduplicate(rawFindings);
        Map<String, Integer> severityCounts = severityCounts(findings);

        return new ArchitectureReviewReport(
                "گزارش بررسی معماری",
                new ArchitectureReviewReport.ExecutiveSummary(
                        findings.size(),
                        severityCounts,
                        mostAffectedDomains(findings)
                ),
                severityGroups(findings),
                technicalExplanations(findings),
                suggestedRefactorings(findings),
                conclusion(findings, severityCounts)
        );
    }

    private Map<String, Integer> severityCounts(List<ViolationFinding> findings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String severity : SEVERITY_ORDER) {
            int count = (int) findings.stream()
                    .filter(finding -> severity.equals(normalizeSeverity(finding.severity())))
                    .count();
            if (count > 0) {
                counts.put(severity, count);
            }
        }
        return Map.copyOf(counts);
    }

    private List<String> mostAffectedDomains(List<ViolationFinding> findings) {
        return findings.stream()
                .map(ViolationFinding::domain)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<ArchitectureReviewReport.SeverityGroup> severityGroups(
            List<ViolationFinding> findings) {
        List<ArchitectureReviewReport.SeverityGroup> result = new ArrayList<>();
        for (String severity : SEVERITY_ORDER) {
            List<ArchitectureReviewReport.ViolationEntry> entries = findings.stream()
                    .filter(finding -> severity.equals(normalizeSeverity(finding.severity())))
                    .sorted(Comparator.comparing(
                                    ViolationFinding::ruleId,
                                    Comparator.nullsLast(String::compareTo)
                            ).thenComparing(
                                    ViolationFinding::file,
                                    Comparator.nullsLast(String::compareTo)
                            ))
                    .map(this::toEntry)
                    .toList();
            if (!entries.isEmpty()) {
                result.add(new ArchitectureReviewReport.SeverityGroup(severity, entries));
            }
        }
        return List.copyOf(result);
    }

    private ArchitectureReviewReport.ViolationEntry toEntry(ViolationFinding finding) {
        return new ArchitectureReviewReport.ViolationEntry(
                finding.ruleId(),
                finding.standard(),
                finding.domain(),
                finding.file(),
                finding.line(),
                finding.confidence() == null ? 0.0 : finding.confidence(),
                safe(finding.evidence())
        );
    }

    private List<ArchitectureReviewReport.TechnicalExplanation> technicalExplanations(
            List<ViolationFinding> findings) {
        return firstFindingByRule(findings).values().stream()
                .map(finding -> new ArchitectureReviewReport.TechnicalExplanation(
                        finding.ruleId(),
                        safe(finding.evidence()),
                        "شواهد ثبت‌شده نشان می‌دهد قانون معماری "
                                + safe(finding.ruleId())
                                + " رعایت نشده است."
                ))
                .toList();
    }

    private List<ArchitectureReviewReport.SuggestedRefactoring> suggestedRefactorings(
            List<ViolationFinding> findings) {
        return firstFindingByRule(findings).values().stream()
                .map(finding -> new ArchitectureReviewReport.SuggestedRefactoring(
                        finding.ruleId(),
                        safe(finding.recommendation()),
                        ""
                ))
                .toList();
    }

    private Map<String, ViolationFinding> firstFindingByRule(List<ViolationFinding> findings) {
        return findings.stream()
                .filter(finding -> finding.ruleId() != null && !finding.ruleId().isBlank())
                .collect(Collectors.toMap(
                        ViolationFinding::ruleId,
                        Function.identity(),
                        (first, duplicate) -> first,
                        LinkedHashMap::new
                ));
    }

    private String conclusion(
            List<ViolationFinding> findings,
            Map<String, Integer> severityCounts) {
        if (findings.isEmpty()) {
            return "در بررسی انجام‌شده نقض معماری ثبت نشد.";
        }
        int critical = severityCounts.getOrDefault("BLOCKER", 0)
                + severityCounts.getOrDefault("MAJOR", 0);
        return "در مجموع " + findings.size() + " نقض معماری یکتا شناسایی شد. "
                + critical + " مورد دارای اولویت بالا است و باید زودتر بررسی شود.";
    }

    private String normalizeSeverity(String severity) {
        return SeverityPolicy.normalize(severity);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
