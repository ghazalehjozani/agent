package com.example.ai01.model;

import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;

import java.util.List;
import java.util.UUID;

public record EmbeddingStoreRequest(UUID projectId,
                                    PackageNode projectRoot,
                                    List<ArchitectureRule> rules) {
    public boolean hasProject() {
        return projectId != null && projectRoot != null;
    }
    public boolean hasRules() {
        return rules != null && !rules.isEmpty();
    }
}