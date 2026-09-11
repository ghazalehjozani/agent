package com.example.ai01.service.batch.impl;

import com.example.ai01.agent.model.ruleextraction.RuleScope;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.model.RuleProjectMatch;
import com.example.ai01.service.batch.BatchSupport;
import com.example.ai01.service.batch.BatchingProperties;
import com.example.ai01.service.batch.RuleBatchingStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(
        name = "app.batching.strategy",
        havingValue = "adaptive"
)
public class AdaptiveBatchingStrategy
        implements RuleBatchingStrategy {

    private final BatchingProperties properties;

    public AdaptiveBatchingStrategy(
            BatchingProperties properties) {

        this.properties = properties;
    }

    @Override
    public List<RuleCheckBatch> buildBatches(
            List<RuleProjectMatch> matches) {

        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        validateProperties();

        List<RuleProjectMatch> validMatches =
                validSortedMatches(matches);

        List<RuleProjectMatch> projectMatches =
                validMatches.stream()
                        .filter(this::isProjectRule)
                        .toList();

        Map<String, List<RuleProjectMatch>> matchesByContext =
                validMatches.stream()
                        .filter(match -> !isProjectRule(match))
                        .collect(Collectors.groupingBy(
                                this::contextKey,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<ContextRuleGroup> contextGroups =
                matchesByContext.entrySet()
                        .stream()
                        .flatMap(entry ->
                                createContextGroups(
                                        entry.getKey(),
                                        selectMatchesForContext(
                                                entry.getValue()
                                        )
                                ).stream()
                        )
                        .sorted(
                                Comparator.comparingInt(
                                        ContextRuleGroup::estimatedTokens
                                ).reversed()
                        )
                        .toList();

        List<MutableBatch> mutableBatches =
                packContextGroups(contextGroups);

        List<RuleCheckBatch> result =
                new ArrayList<>();

        for (int index = 0;
             index < mutableBatches.size();
             index++) {

            result.add(
                    mutableBatches.get(index)
                            .toRuleCheckBatch(
                                    "ADAPTIVE:" + index
                            )
            );
        }

        /*
         * PROJECT Ruleها نیز به چند batch محدودشده
         * تقسیم می‌شوند و دیگر یک batch بزرگ اجباری نداریم.
         */
        result.addAll(
                createProjectBatches(projectMatches)
        );

        return List.copyOf(result);
    }

    private List<MutableBatch> packContextGroups(
            List<ContextRuleGroup> groups) {

        List<MutableBatch> batches =
                new ArrayList<>();

        for (ContextRuleGroup group : groups) {

            MutableBatch bestBatch =
                    findBestBatch(
                            batches,
                            group
                    );

            if (bestBatch != null) {
                bestBatch.add(group);
                continue;
            }

            MutableBatch newBatch =
                    new MutableBatch(properties);

            if (!newBatch.canAccept(group)) {
                throw oversizedContextException(group);
            }

            newBatch.add(group);
            batches.add(newBatch);
        }

        return batches;
    }

    /*
     * Project Ruleها بر اساس Rule گروه‌بندی و سپس با رعایت
     * تمام محدودیت‌ها partition می‌شوند.
     */
    private List<RuleCheckBatch> createProjectBatches(
            List<RuleProjectMatch> projectMatches) {

        if (projectMatches == null
                || projectMatches.isEmpty()) {

            return List.of();
        }

        List<List<RuleProjectMatch>> partitions =
                partitionByLimits(
                        "PROJECT",
                        validSortedMatches(projectMatches)
                );

        List<RuleCheckBatch> batches =
                new ArrayList<>();

        for (int index = 0;
             index < partitions.size();
             index++) {

            batches.add(
                    BatchSupport.createBatch(
                            "PROJECT:"
                                    + (index + 1),
                            partitions.get(index)
                    )
            );
        }

        return List.copyOf(batches);
    }

    private List<ContextRuleGroup> createContextGroups(
            String contextKey,
            List<RuleProjectMatch> matches) {

        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        List<List<RuleProjectMatch>> partitions =
                partitionByLimits(
                        contextKey,
                        matches
                );

        List<ContextRuleGroup> result =
                new ArrayList<>();

        for (int index = 0;
             index < partitions.size();
             index++) {

            List<RuleProjectMatch> partition =
                    partitions.get(index);

            String partitionKey =
                    partitions.size() == 1
                            ? contextKey
                            : contextKey
                            + "#part-"
                            + (index + 1);

            result.add(
                    createContextGroup(
                            partitionKey,
                            partition
                    )
            );
        }

        return List.copyOf(result);
    }

    /*
     * Ruleها تا جای ممکن اتمی نگه داشته می‌شوند.
     * اگر مجموعه Matchهای یک Rule بزرگ باشد، همان Rule
     * نیز بر اساس Segment تقسیم می‌شود.
     */
    private List<List<RuleProjectMatch>> partitionByLimits(
            String contextKey,
            List<RuleProjectMatch> matches) {

        Map<String, List<RuleProjectMatch>> matchesByRule =
                matches.stream()
                        .collect(Collectors.groupingBy(
                                match -> match.rule().id(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<List<RuleProjectMatch>> partitions =
                new ArrayList<>();

        List<RuleProjectMatch> current =
                new ArrayList<>();

        for (List<RuleProjectMatch> ruleMatches
                : matchesByRule.values()) {

            if (fitsConfiguredLimits(ruleMatches)) {

                List<RuleProjectMatch> candidate =
                        combine(
                                current,
                                ruleMatches
                        );

                if (!current.isEmpty()
                        && !fitsConfiguredLimits(candidate)) {

                    partitions.add(
                            List.copyOf(current)
                    );

                    current.clear();
                }

                current.addAll(ruleMatches);
                continue;
            }

            /*
             * قبل از تقسیم یک Rule بزرگ، partition قبلی
             * بسته می‌شود.
             */
            if (!current.isEmpty()) {

                partitions.add(
                        List.copyOf(current)
                );

                current.clear();
            }

            partitions.addAll(
                    splitOversizedRuleMatches(
                            contextKey,
                            ruleMatches
                    )
            );
        }

        if (!current.isEmpty()) {
            partitions.add(
                    List.copyOf(current)
            );
        }

        return List.copyOf(partitions);
    }

    private List<List<RuleProjectMatch>>
    splitOversizedRuleMatches(
            String contextKey,
            List<RuleProjectMatch> ruleMatches) {

        List<List<RuleProjectMatch>> partitions =
                new ArrayList<>();

        List<RuleProjectMatch> current =
                new ArrayList<>();

        for (RuleProjectMatch match : ruleMatches) {

            List<RuleProjectMatch> singleMatch =
                    List.of(match);

            /*
             * یک Rule-Segment منفرد دیگر قابل تقسیم نیست.
             * در این حالت خود TextSegment باید کوچک‌تر شود.
             */
            if (!fitsConfiguredLimits(singleMatch)) {

                throw new IllegalStateException(
                        """
                                A single rule-segment match exceeds the configured limits.
                                contextKey=%s
                                ruleId=%s
                                segmentId=%s
                                estimatedTokens=%d
                                maxInputTokens=%d
                                maxFiles=%d
                                maxRules=%d
                                maxSegments=%d
                                """.formatted(
                                contextKey,
                                match.rule().id(),
                                BatchSupport.segmentKey(match),
                                BatchSupport.estimatedTokens(
                                        singleMatch
                                ),
                                properties.getMaxInputTokens(),
                                properties.getMaxFiles(),
                                properties.getMaxRules(),
                                properties.getMaxSegments()
                        )
                );
            }

            List<RuleProjectMatch> candidate =
                    combine(
                            current,
                            singleMatch
                    );

            if (!current.isEmpty()
                    && !fitsConfiguredLimits(candidate)) {

                partitions.add(
                        List.copyOf(current)
                );

                current.clear();
            }

            current.add(match);
        }

        if (!current.isEmpty()) {
            partitions.add(
                    List.copyOf(current)
            );
        }

        return List.copyOf(partitions);
    }

    private ContextRuleGroup createContextGroup(
            String contextKey,
            List<RuleProjectMatch> matches) {

        return new ContextRuleGroup(
                contextKey,
                List.copyOf(matches),
                BatchSupport.ruleIds(matches),
                BatchSupport.estimatedTokens(matches)
        );
    }

    private MutableBatch findBestBatch(
            List<MutableBatch> batches,
            ContextRuleGroup group) {

        return batches.stream()
                .filter(batch ->
                        batch.canAccept(group)
                )
                .map(batch ->
                        new BatchCandidate(
                                batch,
                                jaccard(
                                        batch.ruleIds(),
                                        group.ruleIds()
                                )
                        )
                )
                .filter(candidate ->
                        candidate.similarity()
                                >= properties.getMinRuleOverlap()
                )
                .max(
                        Comparator.comparingDouble(
                                BatchCandidate::similarity
                        )
                )
                .map(BatchCandidate::batch)
                .orElse(null);
    }

    private List<RuleProjectMatch> selectMatchesForContext(
            List<RuleProjectMatch> matches) {

        List<RuleProjectMatch> sorted =
                validSortedMatches(matches);

        if (sorted.isEmpty()) {
            return List.of();
        }

        /*
         * بهترین Match هر Rule حفظ می‌شود.
         */
        Map<String, RuleProjectMatch> bestPerRule =
                sorted.stream()
                        .collect(Collectors.toMap(
                                match -> match.rule().id(),
                                match -> match,
                                (best, duplicate) -> best,
                                LinkedHashMap::new
                        ));

        Set<String> selectedSegmentIds =
                bestPerRule.values()
                        .stream()
                        .map(BatchSupport::segmentKey)
                        .collect(Collectors.toCollection(
                                LinkedHashSet::new
                        ));

        /*
         * Segmentهای پُرامتیاز اضافی تا سقف تنظیم‌شده
         * انتخاب می‌شوند. بهترین Match Ruleها حذف نمی‌شوند.
         */
        for (RuleProjectMatch match : sorted) {

            if (selectedSegmentIds.size()
                    >= properties.getMaxSegmentsPerFile()) {
                break;
            }

            selectedSegmentIds.add(
                    BatchSupport.segmentKey(match)
            );
        }

        return sorted.stream()
                .filter(match ->
                        selectedSegmentIds.contains(
                                BatchSupport.segmentKey(match)
                        )
                )
                .toList();
    }

    private List<RuleProjectMatch> validSortedMatches(
            List<RuleProjectMatch> matches) {

        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        return matches.stream()
                .filter(Objects::nonNull)
                .filter(match ->
                        match.rule() != null
                )
                .filter(match ->
                        match.rule().id() != null
                                && !match.rule().id().isBlank()
                )
                .filter(match ->
                        match.projectSegment() != null
                )
                .sorted(
                        Comparator.comparingDouble(
                                RuleProjectMatch::score
                        ).reversed()
                )
                .toList();
    }

    private boolean fitsConfiguredLimits(
            List<RuleProjectMatch> matches) {

        if (matches == null || matches.isEmpty()) {
            return true;
        }

        int fileCount =
                distinctFileCount(matches);

        int ruleCount =
                BatchSupport.ruleIds(matches)
                        .size();

        int segmentCount =
                BatchSupport.segmentIds(matches)
                        .size();

        int estimatedTokens =
                BatchSupport.estimatedTokens(matches);

        return fileCount
                <= properties.getMaxFiles()
                && ruleCount
                <= properties.getMaxRules()
                && segmentCount
                <= properties.getMaxSegments()
                && estimatedTokens
                <= properties.getMaxInputTokens();
    }

    private int distinctFileCount(
            List<RuleProjectMatch> matches) {

        return (int) matches.stream()
                .map(RuleProjectMatch::filePath)
                .filter(Objects::nonNull)
                .filter(path -> !path.isBlank())
                .distinct()
                .count();
    }

    private List<RuleProjectMatch> combine(
            List<RuleProjectMatch> first,
            List<RuleProjectMatch> second) {

        List<RuleProjectMatch> combined =
                new ArrayList<>(
                        first.size() + second.size()
                );

        combined.addAll(first);
        combined.addAll(second);

        return combined;
    }

    private IllegalStateException oversizedContextException(
            ContextRuleGroup group) {

        return new IllegalStateException(
                """
                        A batching context exceeds the configured limits
                        after adaptive splitting.
                        contextKey=%s
                        estimatedTokens=%d
                        fileCount=%d
                        ruleCount=%d
                        segmentCount=%d
                        maxInputTokens=%d
                        maxFiles=%d
                        maxRules=%d
                        maxSegments=%d
                        """.formatted(
                        group.contextKey(),
                        group.estimatedTokens(),
                        distinctFileCount(group.matches()),
                        group.ruleIds().size(),
                        BatchSupport.segmentIds(
                                group.matches()
                        ).size(),
                        properties.getMaxInputTokens(),
                        properties.getMaxFiles(),
                        properties.getMaxRules(),
                        properties.getMaxSegments()
                )
        );
    }

    private String contextKey(
            RuleProjectMatch match) {

        if (match.filePath() != null
                && !match.filePath().isBlank()) {

            return "FILE:"
                    + match.filePath();
        }

        return "SEGMENT:"
                + BatchSupport.segmentKey(match);
    }

    private boolean isProjectRule(
            RuleProjectMatch match) {

        return match != null
                && match.rule() != null
                && match.rule().semanticMetadata() != null
                && match.rule()
                .semanticMetadata()
                .scope() == RuleScope.PROJECT;
    }

    private double jaccard(
            Set<String> first,
            Set<String> second) {

        if (first.isEmpty() && second.isEmpty()) {
            return 1.0;
        }

        Set<String> intersection =
                new HashSet<>(first);

        intersection.retainAll(second);

        Set<String> union =
                new HashSet<>(first);

        union.addAll(second);

        return union.isEmpty()
                ? 0.0
                : (double) intersection.size()
                / union.size();
    }

    private void validateProperties() {

        if (properties.getMaxInputTokens() <= 0) {
            throw new IllegalStateException(
                    "app.batching.max-input-tokens must be greater than zero"
            );
        }

        if (properties.getMaxFiles() <= 0) {
            throw new IllegalStateException(
                    "app.batching.max-files must be greater than zero"
            );
        }

        if (properties.getMaxRules() <= 0) {
            throw new IllegalStateException(
                    "app.batching.max-rules must be greater than zero"
            );
        }

        if (properties.getMaxSegments() <= 0) {
            throw new IllegalStateException(
                    "app.batching.max-segments must be greater than zero"
            );
        }

        if (properties.getMaxSegmentsPerFile() <= 0) {
            throw new IllegalStateException(
                    "app.batching.max-segments-per-file must be greater than zero"
            );
        }
    }

    private record ContextRuleGroup(
            String contextKey,
            List<RuleProjectMatch> matches,
            Set<String> ruleIds,
            int estimatedTokens) {
    }

    private record BatchCandidate(
            MutableBatch batch,
            double similarity) {
    }

    private static final class MutableBatch {

        private final BatchingProperties properties;

        private final List<ContextRuleGroup> groups =
                new ArrayList<>();

        private final List<RuleProjectMatch> matches =
                new ArrayList<>();

        private MutableBatch(
                BatchingProperties properties) {

            this.properties = properties;
        }

        private boolean canAccept(
                ContextRuleGroup candidate) {

            List<RuleProjectMatch> combined =
                    new ArrayList<>(matches);

            combined.addAll(
                    candidate.matches()
            );

            int contextCount =
                    groups.size() + 1;

            int fileCount =
                    (int) combined.stream()
                            .map(RuleProjectMatch::filePath)
                            .filter(Objects::nonNull)
                            .filter(path -> !path.isBlank())
                            .distinct()
                            .count();

            int ruleCount =
                    BatchSupport.ruleIds(combined)
                            .size();

            int segmentCount =
                    BatchSupport.segmentIds(combined)
                            .size();

            int estimatedTokens =
                    BatchSupport.estimatedTokens(combined);

            return contextCount
                    <= properties.getMaxFiles()
                    && fileCount
                    <= properties.getMaxFiles()
                    && ruleCount
                    <= properties.getMaxRules()
                    && segmentCount
                    <= properties.getMaxSegments()
                    && estimatedTokens
                    <= properties.getMaxInputTokens();
        }

        private void add(
                ContextRuleGroup group) {

            groups.add(group);

            matches.addAll(
                    group.matches()
            );
        }

        private Set<String> ruleIds() {

            return BatchSupport.ruleIds(
                    matches
            );
        }

        private RuleCheckBatch toRuleCheckBatch(
                String batchId) {

            String contexts =
                    groups.stream()
                            .map(
                                    ContextRuleGroup::contextKey
                            )
                            .collect(
                                    Collectors.joining(",")
                            );

            return BatchSupport.createBatch(
                    batchId
                            + "["
                            + contexts
                            + "]",
                    matches
            );
        }
    }
}