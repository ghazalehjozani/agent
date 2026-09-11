package com.example.ai01.service.batch.impl;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleScope;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.model.RuleProjectMatch;
import com.example.ai01.service.batch.BatchSupport;
import com.example.ai01.service.batch.BatchingProperties;
import com.example.ai01.service.batch.RuleBatchingStrategy;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(
        name = "app.batching.strategy",
        havingValue = "file",
        matchIfMissing = true
)
public class FileBatchingStrategy implements RuleBatchingStrategy {
    private BatchingProperties batchingProperties;

    public FileBatchingStrategy(BatchingProperties batchingProperties) {
        this.batchingProperties = batchingProperties;
    }

    @Override
    public List<RuleCheckBatch> buildBatches(
            List<RuleProjectMatch> matches) {

        return matches.stream()
                .collect(Collectors.groupingBy(
                        this::contextKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry ->
                        BatchSupport.createBatch(
                                entry.getKey(),
                                entry.getValue()))
                .toList();
    }

    private String contextKey(RuleProjectMatch match) {

        if (isProjectRule(match)) {
            return "PROJECT";
        }

        if (match.filePath() != null
                && !match.filePath().isBlank()) {
            return "FILE:" + match.filePath();
        }

        return "SEGMENT:" + match.segmentId();
    }
    private boolean isProjectRule(RuleProjectMatch match) {
        return match.rule().semanticMetadata() != null
                && match.rule().semanticMetadata().scope()
                == RuleScope.PROJECT;
    }

}