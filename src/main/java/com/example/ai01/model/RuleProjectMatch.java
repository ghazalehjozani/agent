package com.example.ai01.model;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import dev.langchain4j.data.segment.TextSegment;
public record RuleProjectMatch(ArchitectureRule rule,
                               double score,
                               String segmentId,
                               String segmentType,
                               String filePath,
                               TextSegment projectSegment) {
}