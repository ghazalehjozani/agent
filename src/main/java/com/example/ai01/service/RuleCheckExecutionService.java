package com.example.ai01.service;

import com.example.ai01.agent.RuleCheckAgent;
import com.example.ai01.agent.model.ViolationFindingReport;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.model.ClassRuleCheckBatch;
import com.example.ai01.model.ProjectClassContext;
import com.example.ai01.monitoring.TraceOperation;
import dev.langchain4j.service.output.OutputParsingException;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RuleCheckExecutionService {

    private static final int MAX_ATTEMPTS = 2;

    private final RuleCheckAgent ruleCheckAgent;

    public RuleCheckExecutionService(RuleCheckAgent ruleCheckAgent) {
        this.ruleCheckAgent = ruleCheckAgent;
    }

    @TraceOperation(
            serviceName = "rule-check-agent",
            spanName = "agent.rule-check",
            newSpan = true,
            spanKind = "AGENT"
    )
    public ViolationFindingReport check(ClassRuleCheckBatch batch) {
        String rules = formatRules(batch.rules());
        String targetClasses = formatTargetClasses(batch.classContexts());
        String classTrees = formatClassTrees(batch.classContexts());

        addTraceAttributes(batch);

        OutputParsingException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Span.current().setAttribute("agent.attempt", attempt);

                return ruleCheckAgent.check(
                        rules,
                        targetClasses,
                        classTrees
                );
            } catch (OutputParsingException exception) {
                lastFailure = exception;
                Span.current().addEvent("agent.output-parsing-failed");
            }
        }

        throw lastFailure;
    }

    private void addTraceAttributes(ClassRuleCheckBatch batch) {
        List<String> ruleIds = batch.rules().stream()
                .map(ArchitectureRule::id)
                .toList();

        List<String> classFiles = batch.classContexts().stream()
                .map(ProjectClassContext::filePath)
                .toList();

        Span span = Span.current();

        span.updateName(
                "agent.rule-check [" + String.join(",", ruleIds) + "]"
        );
        span.setAttribute("openinference.span.kind", "AGENT");
        span.setAttribute(AttributeKey.stringArrayKey("rule.ids"), ruleIds);
        span.setAttribute(AttributeKey.stringArrayKey("batch.class_files"), classFiles);
        span.setAttribute("batch.rule_count", (long) batch.rules().size());
        span.setAttribute("batch.class_count", (long) batch.classContexts().size());
    }

    private String formatRules(List<ArchitectureRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "No rules supplied.";
        }

        return rules.stream()
                .map(rule -> "[%s] %s".formatted(
                        safeValue(rule.id()),
                        safeValue(rule.description())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String formatTargetClasses(
            List<ProjectClassContext> contexts) {

        if (contexts == null || contexts.isEmpty()) {
            return "No target classes supplied.";
        }

        return contexts.stream()
                .map(context -> "- %s (%s)".formatted(
                        safeValue(context.className()),
                        safeValue(context.filePath())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String formatClassTrees(
            List<ProjectClassContext> contexts) {

        if (contexts == null || contexts.isEmpty()) {
            return "No class trees supplied.";
        }

        return contexts.stream()
                .map(ProjectClassContext::compactTree)
                .collect(Collectors.joining("\n\n"));
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
