package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DependencyRuleRepository extends AbstractRuleRepository {
    public DependencyRuleRepository(SourceTextProvider sourceTextProvider) { super(sourceTextProvider); }
    public String strategyKey() { return "DEP"; }
    public boolean supports(String code) { return RuleCode.belongsTo(code, "DEP"); }

    public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) {
        String code = RuleCode.normalize(rule.id());
        if (code.endsWith("12007DEP001")) return noServletRequestInServices(rule, project);
        if (code.endsWith("12008DEP002")) return noFieldInjection(rule, project);
        if (code.endsWith("11035DEP001")) return noDirectServiceToServiceCall(rule, project);
        throw new IllegalArgumentException("Unsupported DEP rule: " + rule.id());
    }

    private List<ViolationFinding> noServletRequestInServices(ArchitectureRule rule, PackageNode project) {
        return violations(rule, project,
                file -> inLayer(file, "service"),
                file -> file.imports().stream().noneMatch(value -> value.endsWith("HttpServletRequest"))
                        && file.fields().stream().noneMatch(field -> field.type().contains("HttpServletRequest"))
                        && file.methods().stream().flatMap(method -> method.parameters().stream())
                        .noneMatch(parameter -> parameter.type().contains("HttpServletRequest")),
                "HttpServletRequest is used in the service layer.",
                "Map transport data in the controller and pass a transport-neutral value to the service.");
    }

    private List<ViolationFinding> noFieldInjection(ArchitectureRule rule, PackageNode project) {
        return violations(rule, project,
                file -> true,
                file -> file.fields().stream().noneMatch(field -> field.annotations().stream()
                        .map(Annotation::name).anyMatch(name -> name.equals("Autowired") || name.endsWith(".Autowired"))),
                "@Autowired is used on a field.",
                "Use constructor injection and make dependencies final.");
    }

    private List<ViolationFinding> noDirectServiceToServiceCall(ArchitectureRule rule, PackageNode project) {
        return violations(rule, project,
                file -> inLayer(file, "service"),
                file -> file.imports().stream().noneMatch(value -> value.contains(".service.")
                        && !value.endsWith(file.fileName().replace(".java", ""))),
                "A service imports another service directly.",
                "Route inter-service communication through the configured ESB client.");
    }
}
