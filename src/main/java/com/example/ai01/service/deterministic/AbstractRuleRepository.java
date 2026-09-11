package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractRuleRepository implements DeterministicRuleRepository {
    private static final Pattern QUOTED_LITERAL = Pattern.compile("['\"]([^'\"]+)['\"]");
    protected final SourceTextProvider sourceTextProvider;

    protected AbstractRuleRepository(SourceTextProvider sourceTextProvider) {
        this.sourceTextProvider = sourceTextProvider;
    }

    protected List<JavaFile> files(PackageNode project) {
        List<JavaFile> result = new ArrayList<>();
        collect(project, result);
        return List.copyOf(result);
    }

    private void collect(PackageNode node, List<JavaFile> target) {
        if (node == null) return;
        target.addAll(node.javaFiles());
        node.packages().forEach(child -> collect(child, target));
    }

    protected boolean hasAnnotation(JavaFile file, String name) {
        return file.annotations().stream().map(Annotation::name)
                .anyMatch(value -> value.equals(name) || value.endsWith("." + name));
    }

    protected boolean inLayer(JavaFile file, String layer) {
        String packageName = safe(file.packageName()).toLowerCase(Locale.ROOT);
        return packageName.equals(layer) || packageName.contains("." + layer + ".")
                || packageName.endsWith("." + layer);
    }

    protected List<ViolationFinding> violations(
            ArchitectureRule rule, PackageNode project, Predicate<JavaFile> target,
            Predicate<JavaFile> compliant, String evidence, String recommendation) {
        return files(project).stream().filter(target).filter(compliant.negate())
                .map(file -> finding(rule, file.path(), evidence, recommendation)).toList();
    }

    protected List<ViolationFinding> evaluateSourceContract(ArchitectureRule rule, PackageNode project) {
        List<String> literals = quotedLiterals(rule.description());
        if (literals.isEmpty()) return List.of();
        String description = safe(rule.description()).toLowerCase(Locale.ROOT);
        boolean prohibition = description.contains("forbidden") || description.contains("must not")
                || description.contains("not permitted") || description.contains("is not allowed");
        Map<String, String> sources = sourceTextProvider.readAll(project);
        return sources.entrySet().stream()
                .filter(entry -> isRelevantSource(description, entry.getValue()))
                .filter(entry -> prohibition
                        ? literals.stream().anyMatch(entry.getValue()::contains)
                        : literals.stream().anyMatch(literal -> !entry.getValue().contains(literal)))
                .map(entry -> finding(rule, entry.getKey(),
                        prohibition ? "Forbidden literal found." : "Required literal is missing: " + literals,
                        "Apply the exact contract required by " + rule.id() + "."))
                .toList();
    }

    private boolean isRelevantSource(String description, String source) {
        if (description.contains("request") || description.contains("response") || description.contains("http")) {
            return source.contains("Mapping") || source.contains("ResponseEntity");
        }
        if (description.contains("message") || description.contains("broker") || description.contains("channel")) {
            return source.contains("Kafka") || source.contains("Rabbit") || source.contains("Message");
        }
        return true;
    }

    private List<String> quotedLiterals(String description) {
        List<String> result = new ArrayList<>();
        Matcher matcher = QUOTED_LITERAL.matcher(safe(description));
        while (matcher.find()) result.add(matcher.group(1));
        return List.copyOf(result);
    }

    protected ViolationFinding finding(
            ArchitectureRule rule, String file, String evidence, String recommendation) {
        return finding(rule, file, null, evidence, recommendation);
    }

    protected ViolationFinding finding(
            ArchitectureRule rule, String file, Integer line, String evidence, String recommendation) {
        return new ViolationFinding(rule.id(), RuleCode.family(rule.id()).toLowerCase(Locale.ROOT),
                rule.id(), "MAJOR", file, line, evidence, recommendation, 1.0);
    }

    protected String safe(String value) { return value == null ? "" : value; }
}
