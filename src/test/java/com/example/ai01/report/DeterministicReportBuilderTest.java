package com.example.ai01.report;

import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.ViolationFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicReportBuilderTest {
    private final DeterministicReportBuilder builder =
            new DeterministicReportBuilder(new ViolationDeduplicator(new ViolationNormalizer()));

    @Test
    void buildsAndDeduplicatesReportWithoutLlm() {
        ViolationFinding finding = new ViolationFinding(
                "STD-1", "api", "RULE-1", "MAJOR", "A.java", 10,
                "evidence", "fix it", 0.9
        );

        ArchitectureReviewReport report = builder.build(List.of(finding, finding));

        assertThat(report.executiveSummary().totalViolations()).isEqualTo(1);
        assertThat(report.executiveSummary().countPerSeverity()).containsEntry("MAJOR", 1);
        assertThat(report.violationsBySeverity()).singleElement()
                .satisfies(group -> assertThat(group.violations()).hasSize(1));
        assertThat(report.technicalExplanation()).singleElement()
                .satisfies(item -> assertThat(item.ruleId()).isEqualTo("RULE-1"));
        assertThat(report.suggestedRefactoring()).singleElement()
                .satisfies(item -> assertThat(item.fix()).isEqualTo("fix it"));
    }
    @Test
    void normalizesAndMergesAgentFindingsForSameSourceLocation() {
        ViolationFinding info = new ViolationFinding(
                " API Design Standard ", "Data", "04034DATA001", "info", "src\\Status.java", 3,
                "short", "fix", 0.80
        );
        ViolationFinding major = new ViolationFinding(
                "04034DATA001", "API Data Contract", "04034DATA001", "MAJOR", "src/Status.java", 3,
                "more detailed evidence", "more detailed recommendation", 0.95
        );

        ArchitectureReviewReport report = builder.build(List.of(info, major));

        assertThat(report.executiveSummary().totalViolations()).isEqualTo(1);
        assertThat(report.executiveSummary().countPerSeverity()).containsExactlyEntriesOf(java.util.Map.of("MAJOR", 1));
        assertThat(report.executiveSummary().mostAffectedDomains()).containsExactly("data");
        assertThat(report.violationsBySeverity().get(0).violations().get(0).evidence())
                .isEqualTo("more detailed evidence");
    }

    @Test
    void keepsDifferentUnknownLocationsWhenEvidenceDiffers() {
        ViolationFinding first = new ViolationFinding("S", "api", "R", "MAJOR", "A.java", null,
                "endpoint one", "fix", 1.0);
        ViolationFinding second = new ViolationFinding("S", "api", "R", "MAJOR", "A.java", null,
                "endpoint two", "fix", 1.0);

        assertThat(builder.build(List.of(first, second)).executiveSummary().totalViolations()).isEqualTo(2);
    }

    @Test
    void handlesEmptyReport() {
        ArchitectureReviewReport report = builder.build(null);

        assertThat(report.executiveSummary().totalViolations()).isZero();
        assertThat(report.violationsBySeverity()).isEmpty();
        assertThat(report.conclusion()).contains("نقض معماری ثبت نشد");
    }
}
