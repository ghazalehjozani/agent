package com.example.ai01.report;

import com.example.ai01.agent.model.ViolationFinding;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ViolationDeduplicator {
    private final ViolationNormalizer normalizer;

    public ViolationDeduplicator(ViolationNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<ViolationFinding> deduplicate(List<ViolationFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        Map<ViolationKey, ViolationFinding> unique = new LinkedHashMap<>();
        findings.stream()
                .filter(Objects::nonNull)
                .map(normalizer::normalize)
                .forEach(finding -> unique.merge(keyOf(finding), finding, this::merge));

        return List.copyOf(unique.values());
    }

    private ViolationKey keyOf(ViolationFinding finding) {
        String location = finding.line() == null
                ? "evidence:" + finding.evidence()
                : "line:" + finding.line();
        return new ViolationKey(finding.ruleId(), finding.file(), location);
    }

    private ViolationFinding merge(ViolationFinding first, ViolationFinding second) {
        return new ViolationFinding(
                richer(first.standard(), second.standard()),
                richer(first.domain(), second.domain()),
                first.ruleId(),
                SeverityPolicy.strongest(first.severity(), second.severity()),
                first.file(),
                first.line() != null ? first.line() : second.line(),
                richer(first.evidence(), second.evidence()),
                richer(first.recommendation(), second.recommendation()),
                Math.max(first.confidence(), second.confidence())
        );
    }

    private String richer(String first, String second) {
        String left = first == null ? "" : first;
        String right = second == null ? "" : second;
        return right.length() > left.length() ? right : left;
    }
}
