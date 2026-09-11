package com.example.ai01.model;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

public record RuleCheckBatch(
        String contextKey,
        List<ArchitectureRule> rules,
        List<TextSegment> projectSegments,
        List<RuleCheckAssignment> assignments
) {

    // backward compatibility
    public RuleCheckBatch(

            String contextKey,
            List<ArchitectureRule> rules,
            List<TextSegment> projectSegments
    ) {
        this(contextKey,
                rules,
                projectSegments,
                List.of());
    }

}