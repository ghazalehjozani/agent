package com.example.ai01.report;

import com.example.ai01.agent.model.ViolationFinding;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class ViolationNormalizer {
    private static final Map<String, String> DOMAIN_ALIASES = Map.ofEntries(
            Map.entry("rest-url", "url"),
            Map.entry("api data contract", "data"),
            Map.entry("api data representation", "data")
    );

    public ViolationFinding normalize(ViolationFinding finding) {
        if (finding == null) return null;
        return new ViolationFinding(
                trim(finding.standard()),
                normalizeDomain(finding.domain()),
                trim(finding.ruleId()),
                SeverityPolicy.normalize(finding.severity()),
                normalizePath(finding.file()),
                finding.line(),
                trim(finding.evidence()),
                trim(finding.recommendation()),
                normalizeConfidence(finding.confidence())
        );
    }

    private String normalizeDomain(String domain) {
        String normalized = trim(domain).toLowerCase(Locale.ROOT);
        return DOMAIN_ALIASES.getOrDefault(normalized, normalized);
    }

    private String normalizePath(String path) {
        return trim(path).replace('\\', '/').replaceAll("/{2,}", "/");
    }

    private Double normalizeConfidence(Double confidence) {
        if (confidence == null) return 0.0;
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
