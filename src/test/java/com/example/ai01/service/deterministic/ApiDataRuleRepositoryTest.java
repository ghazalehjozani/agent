package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.AccessModifier;
import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.ClassType;
import com.example.ai01.agent.model.Field;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.Method;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.Parameter;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleLocalMetadata;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ApiDataRuleRepositoryTest {
    private final SourceTextProvider provider = new SourceTextProvider() {
        public String read(JavaFile file) { return ""; }
        public Map<String, String> readAll(PackageNode project) { return Map.of(); }
    };

    @Test void paginationRuleIgnoresNonControllerFiles() {
        JavaFile application = file("Application.java", "com.acme.pay", ClassType.CLASS, List.of(), List.of());
        assertThat(new ApiRuleRepository(provider).evaluate(rule("02016API005", "API"), project(application))).isEmpty();
    }

    @Test void paginationRuleAcceptsOffsetAndRejectsIncompleteEndpoint() {
        Method valid = endpoint("valid", List.of(new Parameter("int", "page"), new Parameter("int", "size")));
        Method invalid = endpoint("invalid", List.of(new Parameter("int", "page")));
        JavaFile controller = file("PaymentController.java", "com.acme.pay.controller", ClassType.CLASS, List.of(valid, invalid), List.of());
        assertThat(new ApiRuleRepository(provider).evaluate(rule("02016API005", "API"), project(controller)))
                .singleElement().satisfies(v -> assertThat(v.evidence()).contains("invalid"));
    }

    @Test void enumDocumentationRuleIgnoresOrdinaryClasses() {
        Field field = new Field(List.of(), AccessModifier.PRIVATE, "String", "calendarIndicator");
        JavaFile dto = file("Payment.java", "com.acme.pay.dto", ClassType.CLASS, List.of(), List.of(field));
        assertThat(new DataRuleRepository(provider).evaluate(rule("04037DATA004", "DATA"), project(dto))).isEmpty();
    }

    @Test void enumDocumentationRuleChecksOnlyAdditionalFields() {
        Field code = new Field(List.of(), AccessModifier.PRIVATE, "String", "code");
        Field documented = new Field(List.of(new Annotation("Schema", Map.of("description", "\"Client category\""))), AccessModifier.PRIVATE, "String", "category");
        Field undocumented = new Field(List.of(), AccessModifier.PRIVATE, "boolean", "terminal");
        JavaFile value = file("Status.java", "com.acme.pay.model", ClassType.ENUM, List.of(), List.of(code, documented, undocumented));
        assertThat(new DataRuleRepository(provider).evaluate(rule("04037DATA004", "DATA"), project(value)))
                .singleElement().satisfies(v -> assertThat(v.evidence()).contains("terminal"));
    }

    private Method endpoint(String name, List<Parameter> parameters) {
        return new Method(name, AccessModifier.PUBLIC, List.of(new Annotation("GetMapping", Map.of())), "Object", parameters, false);
    }
    private ArchitectureRule rule(String id, String key) {
        return new ArchitectureRule(id, "rule", RuleType.DETERMINISTIC, new RuleLocalMetadata(key), null);
    }
    private PackageNode project(JavaFile... files) { return new PackageNode("", "", List.of(), List.of(files)); }
    private JavaFile file(String name, String pkg, ClassType type, List<Method> methods, List<Field> fields) {
        return new JavaFile(name, "/project/" + name, pkg, List.of(), type, List.of(), null,
                List.of(), fields, List.of(), methods, 1, 1);
    }
}
