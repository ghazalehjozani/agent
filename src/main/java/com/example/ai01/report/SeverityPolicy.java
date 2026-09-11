package com.example.ai01.report;

import java.util.List;
import java.util.Locale;

final class SeverityPolicy {
    static final List<String> ORDER = List.of("BLOCKER", "MAJOR", "MINOR", "INFO");

    private SeverityPolicy() { }

    static String normalize(String severity) {
        if (severity == null) return "INFO";
        String value = severity.trim().toUpperCase(Locale.ROOT);
        return ORDER.contains(value) ? value : "INFO";
    }

    static String strongest(String first, String second) {
        String left = normalize(first);
        String right = normalize(second);
        return ORDER.indexOf(left) <= ORDER.indexOf(right) ? left : right;
    }
}
