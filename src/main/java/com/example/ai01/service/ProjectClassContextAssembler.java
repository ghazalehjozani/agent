package com.example.ai01.service;

import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.Constructor;
import com.example.ai01.agent.model.Field;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.Method;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.Parameter;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.model.ClassRuleCheckBatch;
import com.example.ai01.model.ProjectClassContext;
import com.example.ai01.model.RuleCheckAssignment;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.service.batch.BatchingProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converts retrieval-oriented batches into compact, class-oriented Agent input.
 * Vector segments and rule/file assignments remain internal implementation details.
 */
@Component
public class ProjectClassContextAssembler {

    private final BatchingProperties properties;

    public ProjectClassContextAssembler(BatchingProperties properties) {
        this.properties = properties;
    }

    public List<ClassRuleCheckBatch> assemble(
            PackageNode projectRoot,
            List<RuleCheckBatch> retrievalBatches) {

        if (projectRoot == null
                || retrievalBatches == null
                || retrievalBatches.isEmpty()) {
            return List.of();
        }

        Map<String, JavaFile> filesByPath = indexJavaFiles(projectRoot);
        List<ClassRuleCheckBatch> result = new ArrayList<>();

        for (RuleCheckBatch retrievalBatch : groupByRuleKey(retrievalBatches)) {
            List<ProjectClassContext> contexts = resolveClassContexts(
                    retrievalBatch,
                    filesByPath
            );

            if (contexts.isEmpty()) {
                throw new IllegalStateException(
                        "No JavaFile could be resolved for batch: "
                                + retrievalBatch.contextKey()
                );
            }

            result.addAll(partition(retrievalBatch, contexts));
        }

        return List.copyOf(result);
    }

    /**
     * Retrieval may return the same rule in multiple context-oriented batches.
     * Merge those batches before applying file/token limits so every rule is
     * evaluated with one coherent set of project files whenever it fits.
     */
    private List<RuleCheckBatch> groupByRuleKey(
            List<RuleCheckBatch> retrievalBatches) {

        Map<String, RuleBatchAccumulator> grouped = new LinkedHashMap<>();

        for (RuleCheckBatch batch : retrievalBatches) {
            if (batch == null || batch.rules() == null) {
                continue;
            }

            for (ArchitectureRule rule : batch.rules()) {
                if (rule == null || rule.id() == null || rule.id().isBlank()) {
                    continue;
                }

                grouped.computeIfAbsent(
                                rule.id(),
                                ignored -> new RuleBatchAccumulator(rule)
                        )
                        .add(batch);
            }
        }

        return grouped.values().stream()
                .map(RuleBatchAccumulator::toBatch)
                .toList();
    }

    public Optional<JavaFile> loadJavaFile(
            PackageNode projectRoot,
            String filePath) {

        if (projectRoot == null
                || filePath == null
                || filePath.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                indexJavaFiles(projectRoot).get(normalizePath(filePath))
        );
    }

    public ProjectClassContext createContext(JavaFile javaFile) {
        Objects.requireNonNull(javaFile, "javaFile");

        String tree = compactTree(javaFile);

        return new ProjectClassContext(
                javaFile.path(),
                className(javaFile),
                tree
        );
    }

    private List<ProjectClassContext> resolveClassContexts(
            RuleCheckBatch batch,
            Map<String, JavaFile> filesByPath) {

        Set<String> selectedPaths = selectedFilePaths(batch, filesByPath);
        List<String> unresolvedPaths = new ArrayList<>();
        List<ProjectClassContext> contexts = new ArrayList<>();

        for (String path : selectedPaths) {
            JavaFile javaFile = filesByPath.get(normalizePath(path));

            if (javaFile == null) {
                unresolvedPaths.add(path);
                continue;
            }

            contexts.add(createContext(javaFile));
        }

        if (!unresolvedPaths.isEmpty()) {
            throw new IllegalStateException(
                    "Matched files do not exist in the current project tree. "
                            + "batch=" + batch.contextKey()
                            + ", files=" + unresolvedPaths
            );
        }

        return List.copyOf(contexts);
    }

    private Set<String> selectedFilePaths(
            RuleCheckBatch batch,
            Map<String, JavaFile> filesByPath) {

        Set<String> paths = batch.assignments()
                .stream()
                .filter(Objects::nonNull)
                .map(RuleCheckAssignment::filePaths)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(path -> !path.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        batch.projectSegments()
                .stream()
                .filter(Objects::nonNull)
                .forEach(segment -> {
                    String filePath = segment.metadata().getString("filePath");

                    if (filePath != null && !filePath.isBlank()) {
                        paths.add(filePath);
                        return;
                    }

                    String segmentType = segment.metadata().getString("segmentType");
                    String packageName = segment.metadata().getString("packageName");

                    if ("PACKAGE".equals(segmentType)
                            && packageName != null
                            && !packageName.isBlank()) {
                        addPackageFiles(paths, packageName, filesByPath.values());
                    }
                });

        return paths;
    }

    private void addPackageFiles(
            Set<String> target,
            String packageName,
            Collection<JavaFile> projectFiles) {

        String childPackagePrefix = packageName + ".";

        projectFiles.stream()
                .filter(Objects::nonNull)
                .filter(file -> file.path() != null && !file.path().isBlank())
                .filter(file -> packageName.equals(file.packageName())
                        || (file.packageName() != null
                        && file.packageName().startsWith(childPackagePrefix)))
                .map(JavaFile::path)
                .forEach(target::add);
    }
    private List<ClassRuleCheckBatch> partition(
            RuleCheckBatch source,
            List<ProjectClassContext> contexts) {

        List<List<ProjectClassContext>> partitions = new ArrayList<>();
        List<ProjectClassContext> current = new ArrayList<>();

        for (ProjectClassContext context : contexts) {
            List<ProjectClassContext> candidate = new ArrayList<>(current);
            candidate.add(context);

            if (!current.isEmpty() && !fits(source, candidate)) {
                partitions.add(List.copyOf(current));
                current.clear();
            }

            current.add(context);

            if (!fits(source, current)) {
                throw new IllegalStateException(
                        "A compact JavaFile context exceeds batching limits. "
                                + "file=" + context.filePath()
                                + ", estimatedTokens=" + estimateBatchTokens(source, current)
                                + ", maxInputTokens=" + properties.getMaxInputTokens()
                );
            }
        }

        if (!current.isEmpty()) {
            partitions.add(List.copyOf(current));
        }

        return partitions.stream()
                .map(partition -> createBatch(source, partition))
                .toList();
    }

    private ClassRuleCheckBatch createBatch(
            RuleCheckBatch source,
            List<ProjectClassContext> contexts) {

        Set<String> includedPaths = contexts.stream()
                .map(ProjectClassContext::filePath)
                .map(this::normalizePath)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> includedRuleIds = source.assignments().stream()
                .filter(Objects::nonNull)
                .filter(assignment -> assignment.filePaths().stream()
                        .map(this::normalizePath)
                        .anyMatch(includedPaths::contains))
                .map(RuleCheckAssignment::ruleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ArchitectureRule> rules = source.rules().stream()
                .filter(rule -> includedRuleIds.isEmpty()
                        || includedRuleIds.contains(rule.id()))
                .toList();

        return new ClassRuleCheckBatch(rules, contexts);
    }

    private boolean fits(
            RuleCheckBatch source,
            List<ProjectClassContext> contexts) {

        return contexts.size() <= properties.getMaxFiles()
                && estimateBatchTokens(source, contexts)
                <= properties.getMaxInputTokens();
    }

    private int estimateBatchTokens(
            RuleCheckBatch source,
            List<ProjectClassContext> contexts) {

        int characters = contexts.stream()
                .mapToInt(context -> context.compactTree().length())
                .sum();

        characters += source.rules().stream()
                .mapToInt(rule -> safeLength(rule.id())
                        + safeLength(rule.description())
                        + 80)
                .sum();

        return Math.max(1, (characters + 3) / 4);
    }

    private Map<String, JavaFile> indexJavaFiles(PackageNode root) {
        Map<String, JavaFile> result = new LinkedHashMap<>();
        collectJavaFiles(root, result);
        return result;
    }

    private void collectJavaFiles(
            PackageNode node,
            Map<String, JavaFile> target) {

        if (node == null) {
            return;
        }

        node.javaFiles().stream()
                .filter(Objects::nonNull)
                .filter(file -> file.path() != null && !file.path().isBlank())
                .forEach(file -> target.put(normalizePath(file.path()), file));

        node.packages().forEach(child -> collectJavaFiles(child, target));
    }

    private String compactTree(JavaFile file) {
        StringBuilder tree = new StringBuilder();

        tree.append("path: ").append(safe(file.path())).append("\n")
                .append("package: ").append(safe(file.packageName())).append("\n")
                .append("type: ").append(file.classType()).append("\n");

        appendValue(tree, "annotations", formatAnnotations(file.annotations()));
        appendValue(tree, "extends", file.superClass());
        appendList(tree, "implements", file.interfaces());
        appendList(tree, "imports", file.imports());
        appendMembers(tree, "fields", file.fields(), this::formatField);
        appendMembers(tree, "constructors", file.constructors(), this::formatConstructor);
        appendMembers(tree, "methods", file.methods(), this::formatMethod);

        return tree.toString().stripTrailing();
    }

    private String formatField(Field field) {
        return joinNonBlank(
                String.valueOf(field.accessModifier()),
                field.type(),
                field.name(),
                formatAnnotations(field.annotations())
        );
    }

    private String formatConstructor(Constructor constructor) {
        return joinNonBlank(
                String.valueOf(constructor.accessModifier()),
                "constructor(" + formatParameters(constructor.parameters()) + ")",
                formatAnnotations(constructor.annotations())
        );
    }

    private String formatMethod(Method method) {
        return joinNonBlank(
                String.valueOf(method.accessModifier()),
                method.isStatic() ? "static" : "",
                method.returnType(),
                method.name() + "(" + formatParameters(method.parameters()) + ")",
                formatAnnotations(method.annotations())
        );
    }

    private String formatParameters(List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        return parameters.stream()
                .map(parameter -> joinNonBlank(parameter.type(), parameter.name()))
                .collect(Collectors.joining(", "));
    }

    private String formatAnnotations(List<Annotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return "";
        }

        return annotations.stream()
                .map(annotation -> {
                    if (annotation.attributes() == null
                            || annotation.attributes().isEmpty()) {
                        return "@" + annotation.name();
                    }

                    String attributes = annotation.attributes().entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining(","));

                    return "@" + annotation.name() + "(" + attributes + ")";
                })
                .collect(Collectors.joining(" "));
    }

    private <T> void appendMembers(
            StringBuilder target,
            String label,
            List<T> values,
            Function<T, String> formatter) {

        if (values == null || values.isEmpty()) {
            return;
        }

        target.append(label).append(":\n");
        values.stream()
                .map(formatter)
                .filter(value -> !value.isBlank())
                .forEach(value -> target.append("  - ").append(value).append("\n"));
    }

    private void appendList(
            StringBuilder target,
            String label,
            List<String> values) {

        if (values == null || values.isEmpty()) {
            return;
        }

        appendValue(target, label, String.join(", ", values));
    }

    private void appendValue(
            StringBuilder target,
            String label,
            String value) {

        if (value == null || value.isBlank()) {
            return;
        }

        target.append(label)
                .append(": ")
                .append(value)
                .append("\n");
    }

    private String joinNonBlank(String... values) {
        return java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String className(JavaFile file) {
        String fileName = safe(file.fileName());
        return fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private int estimateTokens(String value) {
        return Math.max(1, (safeLength(value) + 3) / 4);
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class RuleBatchAccumulator {

        private final ArchitectureRule rule;
        private final Map<String, dev.langchain4j.data.segment.TextSegment> segments =
                new LinkedHashMap<>();
        private final Set<String> filePaths = new LinkedHashSet<>();
        private final Set<String> segmentIds = new LinkedHashSet<>();

        private RuleBatchAccumulator(ArchitectureRule rule) {
            this.rule = rule;
        }

        private void add(RuleCheckBatch batch) {
            List<RuleCheckAssignment> ruleAssignments = batch.assignments().stream()
                    .filter(Objects::nonNull)
                    .filter(assignment -> rule.id().equals(assignment.ruleId()))
                    .toList();

            ruleAssignments.stream()
                    .map(RuleCheckAssignment::filePaths)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .filter(Objects::nonNull)
                    .filter(path -> !path.isBlank())
                    .forEach(filePaths::add);

            ruleAssignments.stream()
                    .map(RuleCheckAssignment::segmentIds)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .forEach(segmentIds::add);

            batch.projectSegments().stream()
                    .filter(Objects::nonNull)
                    .filter(segment -> ruleAssignments.isEmpty()
                            || segmentIds.contains(segmentKey(segment)))
                    .forEach(segment -> segments.putIfAbsent(
                            segmentKey(segment),
                            segment
                    ));
        }

        private RuleCheckBatch toBatch() {
            return new RuleCheckBatch(
                    "RULE:" + rule.id(),
                    List.of(rule),
                    List.copyOf(segments.values()),
                    List.of(new RuleCheckAssignment(
                            rule.id(),
                            List.copyOf(filePaths),
                            List.copyOf(segmentIds)
                    ))
            );
        }

        private String segmentKey(
                dev.langchain4j.data.segment.TextSegment segment) {

            String segmentId = segment.metadata().getString("segmentId");
            if (segmentId != null && !segmentId.isBlank()) {
                return segmentId;
            }

            return Objects.toString(
                    segment.metadata().getString("filePath"),
                    ""
            ) + "|" + Objects.hashCode(segment.text());
        }
    }
}


