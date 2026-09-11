package com.example.ai01.service.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.batching")
public class BatchingProperties {
    private int maxInputTokens = 12_000;
    private int maxFiles = 5;
    private int maxRules = 10;
    private int maxSegments = 15;
    private int maxSegmentsPerFile = 3;
    private double minRuleOverlap = 0.25;
    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public int getMaxRules() {
        return maxRules;
    }

    public void setMaxRules(int maxRules) {
        this.maxRules = maxRules;
    }

    public int getMaxSegments() {
        return maxSegments;
    }

    public void setMaxSegments(int maxSegments) {
        this.maxSegments = maxSegments;
    }

    public int getMaxSegmentsPerFile() {
        return maxSegmentsPerFile;
    }

    public void setMaxSegmentsPerFile(int maxSegmentsPerFile) {
        this.maxSegmentsPerFile = maxSegmentsPerFile;
    }

    public double getMinRuleOverlap() {
        return minRuleOverlap;
    }

    public void setMinRuleOverlap(double minRuleOverlap) {
        this.minRuleOverlap = minRuleOverlap;
    }
}