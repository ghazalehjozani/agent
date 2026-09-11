package com.example.ai01.agent.model.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

public record EmbeddedRule(String ruleId,
                           Embedding embedding,
                           TextSegment textSegment) {
}