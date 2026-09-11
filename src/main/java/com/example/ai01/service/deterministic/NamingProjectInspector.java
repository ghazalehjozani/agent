package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NamingProjectInspector {
    private static final Pattern MAPPING = Pattern.compile(
            "@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\\s*\\(([^)]*)\\)",
            Pattern.DOTALL
    );
    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']*)[\"']");
    private static final Pattern EXPLICIT_MAPPING_PATH = Pattern.compile(
            "(?:^|,)\\s*(?:value|path)\\s*=\\s*(\\{[^}]*}|[\"'][^\"']*[\"'])",
            Pattern.DOTALL
    );
    private static final Pattern POSITIONAL_MAPPING_PATH = Pattern.compile(
            "^\\s*(\\{[^}]*}|[\"'][^\"']*[\"'])",
            Pattern.DOTALL
    );
    private static final Pattern REQUEST_PARAM = Pattern.compile(
            "@RequestParam(?:\\s*\\(([^)]*)\\))?\\s+[\\w<>?, .]+\\s+(\\w+)"
    );
    private static final Pattern EXPLICIT_NAME = Pattern.compile(
            "(?:value|name)\\s*=\\s*[\"']([^\"']+)[\"']|^[\"']([^\"']+)[\"']$"
    );
    private static final Pattern CHANNEL_LITERAL = Pattern.compile(
            "[\"']([^\"']*(?:\\.event\\.|\\.command\\.|\\.topic\\.|\\.queue\\.)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private final SourceTextProvider sourceTextProvider;

    public NamingProjectInspector(SourceTextProvider sourceTextProvider) {
        this.sourceTextProvider = sourceTextProvider;
    }

    public Inspection inspect(PackageNode project) {
        List<JavaFile> files = new ArrayList<>();
        collect(project, files);
        Map<String, String> sources = sourceTextProvider.readAll(project);
        List<NamedValue> mappings = new ArrayList<>();
        List<NamedValue> queryParameters = new ArrayList<>();
        List<NamedValue> channels = new ArrayList<>();

        sources.forEach((path, source) -> {
            extractMappings(path, source, mappings);
            extractQueryParameters(path, source, queryParameters);
            extractChannels(path, source, channels);
        });

        return new Inspection(
                List.copyOf(files),
                List.copyOf(mappings),
                List.copyOf(queryParameters),
                List.copyOf(channels),
                sources
        );
    }

    public boolean isController(JavaFile file) {
        return hasAnnotation(file, "Controller")
                || hasAnnotation(file, "RestController");
    }

    public boolean isService(JavaFile file) {
        return hasAnnotation(file, "Service");
    }

    public boolean isRepository(JavaFile file) {
        return hasAnnotation(file, "Repository")
                || file.interfaces().stream().anyMatch(value ->
                value.endsWith("Repository") || value.contains("JpaRepository")
                        || value.contains("CrudRepository"));
    }

    public String className(JavaFile file) {
        String name = file.fileName();
        return name != null && name.endsWith(".java")
                ? name.substring(0, name.length() - 5)
                : name;
    }

    public boolean isCamelCase(String value) {
        return value != null && value.matches("[a-z][A-Za-z0-9]*");
    }

    public boolean isKebabCase(String value) {
        return value != null && value.matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }

    public List<String> pathSegments(String path) {
        if (path == null) return List.of();
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .filter(segment -> !(segment.startsWith("{") && segment.endsWith("}")))
                .filter(segment -> !segment.equals("*"))
                .toList();
    }

    private void extractMappings(String path, String source, List<NamedValue> target) {
        Matcher matcher = MAPPING.matcher(source);
        while (matcher.find()) {
            String arguments = matcher.group(2);
            Matcher pathAttribute = EXPLICIT_MAPPING_PATH.matcher(arguments);
            boolean explicitPathFound = false;
            while (pathAttribute.find()) {
                explicitPathFound = true;
                addQuotedPaths(path, source, matcher.start(), pathAttribute.group(1), target);
            }
            if (!explicitPathFound) {
                Matcher positionalPath = POSITIONAL_MAPPING_PATH.matcher(arguments);
                if (positionalPath.find()) {
                    addQuotedPaths(path, source, matcher.start(), positionalPath.group(1), target);
                }
            }
        }
    }

    private void addQuotedPaths(String path, String source, int annotationOffset,
                                String pathExpression, List<NamedValue> target) {
        Matcher quoted = QUOTED.matcher(pathExpression);
        while (quoted.find()) {
            String value = quoted.group(1);
            if (!value.isBlank()) {
                target.add(new NamedValue(path, value, line(source, annotationOffset)));
            }
        }
    }

    private void extractQueryParameters(String path, String source, List<NamedValue> target) {
        Matcher matcher = REQUEST_PARAM.matcher(source);
        while (matcher.find()) {
            String annotation = matcher.group(1);
            String name = matcher.group(2);
            if (annotation != null) {
                Matcher explicit = EXPLICIT_NAME.matcher(annotation.trim());
                if (explicit.find()) name = explicit.group(1) != null ? explicit.group(1) : explicit.group(2);
            }
            target.add(new NamedValue(path, name, line(source, matcher.start())));
        }
    }

    private void extractChannels(String path, String source, List<NamedValue> target) {
        Matcher matcher = CHANNEL_LITERAL.matcher(source);
        while (matcher.find()) target.add(new NamedValue(path, matcher.group(1), line(source, matcher.start())));
    }

    private int line(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) if (source.charAt(index) == '\n') line++;
        return line;
    }

    private void collect(PackageNode node, List<JavaFile> target) {
        if (node == null) return;
        target.addAll(node.javaFiles());
        node.packages().forEach(child -> collect(child, target));
    }

    private boolean hasAnnotation(JavaFile file, String expected) {
        return file.annotations().stream().map(Annotation::name)
                .anyMatch(name -> name.equals(expected) || name.endsWith("." + expected));
    }

    public record NamedValue(String file, String value, int line) {
    }

    public record Inspection(
            List<JavaFile> files,
            List<NamedValue> mappings,
            List<NamedValue> queryParameters,
            List<NamedValue> channels,
            Map<String, String> sources
    ) {
    }
}
