package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class LayerRuleRepository extends AbstractRuleRepository {
    public LayerRuleRepository(SourceTextProvider sourceTextProvider) { super(sourceTextProvider); }
    public String strategyKey() { return "LAYER"; }
    public boolean supports(String code) { return RuleCode.belongsTo(code, "LAYER"); }

    public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) {
        return switch (RuleCode.normalize(rule.id()).replaceFirst("^\\d+LAYER", "")) {
            case "001" -> forbiddenDependencies(rule, project, "controller", List.of("controller", "repository"));
            case "002" -> forbiddenDependencies(rule, project, "service", List.of("controller", "service"));
            case "003" -> forbiddenDependencies(rule, project, "repository", List.of("service"));
            default -> throw new IllegalArgumentException("Unsupported LAYER rule: " + rule.id());
        };
    }

    private List<ViolationFinding> forbiddenDependencies(
            ArchitectureRule rule, PackageNode project, String sourceLayer, List<String> forbiddenLayers) {
        List<JavaFile> allFiles = files(project);
        List<ViolationFinding> result = new ArrayList<>();
        for (JavaFile source : allFiles) {
            if (!isLayer(source, sourceLayer)) continue;
            String sourceText = sourceTextProvider.read(source);
            for (JavaFile target : allFiles) {
                String targetLayer = layerOf(target);
                if (source == target || !forbiddenLayers.contains(targetLayer)) continue;
                String typeName = typeName(target);
                if (referencesType(source, sourceText, target, typeName)) {
                    result.add(finding(rule, source.path(),
                            typeName + " from the " + targetLayer + " layer is referenced by " + typeName(source) + ".",
                            "Route the dependency through the allowed adjacent layer."));
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean referencesType(JavaFile source, String text, JavaFile target, String typeName) {
        String qualifiedName = safe(target.packageName()) + "." + typeName;
        boolean imported = source.imports().stream().anyMatch(value -> value.equals(qualifiedName));
        boolean samePackage = safe(source.packageName()).equals(safe(target.packageName()));
        boolean used = Pattern.compile("\\b" + Pattern.quote(typeName) + "\\b").matcher(safe(text)).find();
        return used && (imported || samePackage || safe(text).contains(qualifiedName));
    }

    private boolean isLayer(JavaFile file, String layer) {
        if (inLayer(file, layer)) return true;
        String annotation = switch (layer) {
            case "controller" -> "Controller";
            case "service" -> "Service";
            case "repository" -> "Repository";
            default -> "";
        };
        return hasAnnotation(file, annotation) || (layer.equals("controller") && hasAnnotation(file, "RestController"));
    }

    private String layerOf(JavaFile file) {
        for (String layer : List.of("controller", "service", "repository")) if (isLayer(file, layer)) return layer;
        return "other";
    }

    private String typeName(JavaFile file) {
        String name = safe(file.fileName());
        return name.endsWith(".java") ? name.substring(0, name.length() - 5) : name;
    }
}
