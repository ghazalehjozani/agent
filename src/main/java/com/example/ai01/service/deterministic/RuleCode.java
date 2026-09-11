package com.example.ai01.service.deterministic;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RuleCode {
    private static final Pattern FAMILY = Pattern.compile("([A-Z]+)(\\d+)$");

    private RuleCode() {
    }

    static String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    static String family(String value) {
        Matcher matcher = FAMILY.matcher(normalize(value));
        return matcher.find() ? matcher.group(1) : "";
    }

    static boolean belongsTo(String value, String... families) {
        String actual = family(value);
        for (String family : families) {
            if (actual.equals(family)) {
                return true;
            }
        }
        return false;
    }
}
