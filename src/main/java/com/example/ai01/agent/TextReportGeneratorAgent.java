package com.example.ai01.agent;

import com.example.ai01.agent.model.ViolationFindingReport;
import dev.langchain4j.service.SystemMessage;

public interface TextReportGeneratorAgent {
    @SystemMessage("""
            Generate a professional architecture code review report in Markdown,
            written in Persian (Farsi).

            Input: a list of violation findings. Each finding has: standard, domain,
            ruleId, severity (BLOCKER/MAJOR/MINOR/INFO), file, line, evidence,
            recommendation, confidence.

            The report must include, in this order:
            1. # Title
            2. ## Executive Summary — total violation count, count per severity,
               and the most affected domains
            3. ## Violations grouped by severity (BLOCKER first, then MAJOR,
               MINOR, INFO). Render each group as a Markdown table with columns:
               ruleId | standard | domain | file | line | confidence
            4. ## Technical explanation — for each violation, explain the evidence
               and why it breaks the rule. Reference findings by their ruleId.
            5. ## Suggested refactoring — concrete fixes based on each finding's
               recommendation field, with code/path examples where useful
            6. ## Final conclusion — overall compliance assessment

            Rules:
            - Do not add, remove or alter any violation; report exactly what is given.
            - If line is null, show "-" in the table.
            - Show confidence as a percentage (e.g. 0.94 -> 94%).
            - If the list is empty, produce a short report stating full compliance.
            """)
    String generateReport(ViolationFindingReport violationFindingReport);

}