package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class PackageRuleRepository extends AbstractRuleRepository {
    private static final Pattern PACKAGE = Pattern.compile("com\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");

    public PackageRuleRepository(SourceTextProvider provider) { super(provider); }
    public String strategyKey() { return "PKG"; }
    public boolean supports(String code) { return RuleCode.belongsTo(code, "PKG"); }

    public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) {
        if (!RuleCode.normalize(rule.id()).endsWith("PKG001"))
            throw new IllegalArgumentException("Unsupported PKG rule: " + rule.id());
        return violations(rule, project, file -> !hasAnnotation(file, "SpringBootApplication"), this::validPackage,
                "Package declaration or source path does not follow com.{company}.{project}.{layer}.",
                "Use a lowercase com.{company}.{project}.{layer} package and align it with src/main/java.");
    }

    private boolean validPackage(JavaFile file) {
        String packageName = safe(file.packageName());
        if (!PACKAGE.matcher(packageName).matches()) return false;
        String normalizedPath = safe(file.path()).replace('\\', '/');
        String marker = "/src/main/java/";
        int start = normalizedPath.indexOf(marker);
        if (start < 0) return true;
        int lastSlash = normalizedPath.lastIndexOf('/');
        String directory = normalizedPath.substring(start + marker.length(), lastSlash).replace('/', '.');
        return directory.equals(packageName);
    }
}
