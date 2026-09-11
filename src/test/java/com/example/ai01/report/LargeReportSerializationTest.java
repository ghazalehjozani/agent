package com.example.ai01.report;

import com.example.ai01.agent.model.ArchitectureReviewReport;
import com.example.ai01.agent.model.ViolationFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LargeReportSerializationTest {
    @Test
    void serializesTwoHundredViolationsAsCompleteJson() throws Exception {
        List<ViolationFinding> findings = IntStream.range(0, 200)
                .mapToObj(index -> new ViolationFinding(
                        "STD-" + index,
                        "api",
                        "RULE-" + index,
                        "MAJOR",
                        "File" + index + ".java",
                        index + 1,
                        "evidence " + index,
                        "recommendation " + index,
                        1.0
                ))
                .toList();

        ArchitectureReviewReport report = new DeterministicReportBuilder(
                new ViolationDeduplicator(new ViolationNormalizer())
        ).build(findings);

        String json = new ObjectMapper().writeValueAsString(report);

        assertThat(report.executiveSummary().totalViolations()).isEqualTo(200);
        assertThat(json).startsWith("{").endsWith("}");
        assertThat(new ObjectMapper().readTree(json).isObject()).isTrue();
    }
}
