package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.ClassType;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PatternRuleRepository extends AbstractRuleRepository {
    private static final Pattern THROWN_EXCEPTION = Pattern.compile("(?:throw\\s+new\\s+|throws\\s+)([A-Z][A-Za-z0-9_]*Exception)\\b");

    public PatternRuleRepository(SourceTextProvider provider) { super(provider); }
    public String strategyKey() { return "PATTERN"; }
    public boolean supports(String code) { return RuleCode.belongsTo(code, "PATTERN"); }

    public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) {
        return switch (RuleCode.normalize(rule.id()).replaceFirst("^\\d+PATTERN", "")) {
            case "001" -> controllerAdviceCoverage(rule, project);
            case "002" -> entityAnnotations(rule, project);
            default -> throw new IllegalArgumentException("Unsupported PATTERN rule: " + rule.id());
        };
    }

    private List<ViolationFinding> controllerAdviceCoverage(ArchitectureRule rule, PackageNode project) {
        Set<String> thrown = files(project).stream()
                .filter(this::isHttpBoundary)
                .map(sourceTextProvider::read)
                .flatMap(source -> matches(THROWN_EXCEPTION, source).stream())
                .collect(Collectors.toSet());
        if (thrown.isEmpty()) return List.of();
        List<JavaFile> adviceFiles = files(project).stream()
                .filter(file -> hasAnnotation(file, "ControllerAdvice") || hasAnnotation(file, "RestControllerAdvice"))
                .toList();
        if (adviceFiles.isEmpty()) {
            return List.of(finding(rule, "<project>",
                    "No @ControllerAdvice handles the project exceptions: " + thrown,
                    "Add a centralized @ControllerAdvice with @ExceptionHandler methods."));
        }
        String handlers = adviceFiles.stream().map(sourceTextProvider::read).collect(Collectors.joining("\n"));
        boolean catchAll = handlers.matches("(?s).*@ExceptionHandler\\s*\\([^)]*\\b(?:Exception|Throwable)\\s*\\.class[^)]*\\).*" );
        if (catchAll) return List.of();
        List<ViolationFinding> result = new ArrayList<>();
        for (String exception : thrown) {
            if (!Pattern.compile("@ExceptionHandler\\s*\\([^)]*\\b" + Pattern.quote(exception) + "\\s*\\.class", Pattern.DOTALL)
                    .matcher(handlers).find()) {
                result.add(finding(rule, "<project>", "No @ExceptionHandler covers " + exception + ".",
                        "Handle " + exception + " in a @ControllerAdvice class."));
            }
        }
        return List.copyOf(result);
    }
    private boolean isHttpBoundary(JavaFile file) {
        return hasAnnotation(file, "Controller") || hasAnnotation(file, "RestController")
                || inLayer(file, "controller") || inLayer(file, "web");
    }


    private List<String> matches(Pattern pattern, String source) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(safe(source));
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private List<ViolationFinding> entityAnnotations(ArchitectureRule rule, PackageNode project) {
        return violations(rule, project, this::isEntityCandidate,
                file -> hasAnnotation(file, "Entity") && hasAnnotation(file, "Table"),
                "Persistence entity must declare both @Entity and @Table.",
                "Add @Entity and @Table(name = \"...\") to the entity class.");
    }

    private boolean isEntityCandidate(JavaFile file) {
        return (file.classType() == ClassType.CLASS || file.classType() == ClassType.RECORD)
                && (hasAnnotation(file, "Entity") || inLayer(file, "entity") || inLayer(file, "entities"));
    }
}
