package com.example.ai01.service.batch;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.model.RuleCheckAssignment;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.model.RuleProjectMatch;
import dev.langchain4j.data.segment.TextSegment;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BatchSupport {

    private BatchSupport() {
    }

    public static RuleCheckBatch createBatch(
            String contextKey,
            Collection<RuleProjectMatch> matches) {

        List<RuleProjectMatch> validMatches =
                matches.stream()
                        .filter(Objects::nonNull)
                        .filter(match -> match.rule() != null)
                        .filter(match -> match.projectSegment() != null)
                        .sorted(
                                Comparator.comparingDouble(
                                        RuleProjectMatch::score
                                ).reversed()
                        )
                        .toList();

        Map<String, ArchitectureRule> uniqueRules =
                validMatches.stream()
                        .map(RuleProjectMatch::rule)
                        .collect(Collectors.toMap(
                                ArchitectureRule::id,
                                Function.identity(),
                                (first, duplicate) -> first,
                                LinkedHashMap::new
                        ));

        Map<String, TextSegment> uniqueSegments =
                validMatches.stream()
                        .collect(Collectors.toMap(
                                BatchSupport::segmentKey,
                                RuleProjectMatch::projectSegment,
                                (first, duplicate) -> first,
                                LinkedHashMap::new
                        ));

        List<RuleCheckAssignment> assignments =
                createAssignments(validMatches);

        return new RuleCheckBatch(
                contextKey,
                List.copyOf(uniqueRules.values()),
                List.copyOf(uniqueSegments.values()),
                assignments
        );
    }

    public static Set<String> ruleIds(
            Collection<RuleProjectMatch> matches) {

        return matches.stream()
                .map(RuleProjectMatch::rule)
                .filter(Objects::nonNull)
                .map(ArchitectureRule::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(
                        LinkedHashSet::new
                ));
    }

    public static Set<String> segmentIds(
            Collection<RuleProjectMatch> matches) {

        return matches.stream()
                .map(BatchSupport::segmentKey)
                .collect(Collectors.toCollection(
                        LinkedHashSet::new
                ));
    }

    public static int estimatedTokens(
            Collection<RuleProjectMatch> matches) {

        Map<String, ArchitectureRule> rules =
                matches.stream()
                        .map(RuleProjectMatch::rule)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                ArchitectureRule::id,
                                Function.identity(),
                                (first, duplicate) -> first
                        ));

        Map<String, TextSegment> segments =
                matches.stream()
                        .filter(match ->
                                match.projectSegment() != null)
                        .collect(Collectors.toMap(
                                BatchSupport::segmentKey,
                                RuleProjectMatch::projectSegment,
                                (first, duplicate) -> first
                        ));

        int characters = 0;

        for (ArchitectureRule rule : rules.values()) {
            characters += safeLength(rule.id());
            characters += safeLength(rule.description());
            characters += 100; // metadata و prompt overhead
        }

        for (TextSegment segment : segments.values()) {
            characters += safeLength(segment.text());
            characters += 100;
        }

        // تخمین ساده و محافظه‌کارانه
        return Math.max(1, (characters + 3) / 4);
    }

    public static String segmentKey(
            RuleProjectMatch match) {

        if (match.segmentId() != null
                && !match.segmentId().isBlank()) {
            return match.segmentId();
        }

        TextSegment segment =
                match.projectSegment();

        if (segment != null
                && segment.metadata() != null) {

            String metadataId =
                    segment.metadata()
                            .getString("segmentId");

            if (metadataId != null
                    && !metadataId.isBlank()) {
                return metadataId;
            }
        }

        return Objects.toString(match.filePath(), "")
                + "|"
                + Objects.toString(match.segmentType(), "")
                + "|"
                + Objects.hashCode(
                segment == null ? null : segment.text()
        );
    }

    private static List<RuleCheckAssignment> createAssignments(
            List<RuleProjectMatch> matches) {

        Map<String, AssignmentAccumulator> grouped =
                new LinkedHashMap<>();

        for (RuleProjectMatch match : matches) {

            String ruleId =
                    match.rule().id();

            AssignmentAccumulator accumulator =
                    grouped.computeIfAbsent(
                            ruleId,
                            ignored ->
                                    new AssignmentAccumulator()
                    );

            if (match.filePath() != null
                    && !match.filePath().isBlank()) {
                accumulator.filePaths.add(
                        match.filePath()
                );
            }

            accumulator.segmentIds.add(
                    segmentKey(match)
            );
        }

        return grouped.entrySet()
                .stream()
                .map(entry ->
                        new RuleCheckAssignment(
                                entry.getKey(),
                                List.copyOf(
                                        entry.getValue().filePaths
                                ),
                                List.copyOf(
                                        entry.getValue().segmentIds
                                )
                        )
                )
                .toList();
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static final class AssignmentAccumulator {

        private final Set<String> filePaths =
                new LinkedHashSet<>();

        private final Set<String> segmentIds =
                new LinkedHashSet<>();
    }
}