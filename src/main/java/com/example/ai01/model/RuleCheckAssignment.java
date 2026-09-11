package com.example.ai01.model;

import java.util.List;
public record RuleCheckAssignment(
        String ruleId,
        List<String> filePaths,
        List<String> segmentIds
) {
}