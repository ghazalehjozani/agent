package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.AccessModifier;
import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.ClassType;
import com.example.ai01.agent.model.Field;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleLocalMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NamingRuleRepositoryTest {
    @Test
    void identifiesServiceBeforeCheckingSuffix() {
        JavaFile service = javaFile(
                "PaymentManager.java",
                "com.acme.payments.service",
                List.of(new Annotation("Service", Map.of())),
                List.of()
        );
        NamingRuleRepository repository = repository(Map.of(service.path(), "@Service class PaymentManager {}"));

        assertThat(repository.evaluate(rule("12005NAME002"), project(service)))
                .singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(service.path()));
    }

    @Test
    void ignoresNonServiceForServiceSuffixRule() {
        JavaFile dto = javaFile(
                "Payment.java",
                "com.acme.payments.dto",
                List.of(),
                List.of()
        );

        assertThat(repository(Map.of()).evaluate(rule("12005NAME002"), project(dto))).isEmpty();
    }

    @Test
    void ignoresUnannotatedClassesInsideServicePackage() {
        JavaFile event = javaFile("HotelServiceEvent.java", "com.acme.hotel.service", List.of(), List.of());
        JavaFile health = javaFile("HotelServiceHealth.java", "com.acme.hotel.service",
                List.of(new Annotation("Component", Map.of())), List.of());
        JavaFile properties = javaFile("ServiceProperties.java", "com.acme.hotel.service",
                List.of(new Annotation("ConfigurationProperties", Map.of())), List.of());

        assertThat(repository(Map.of()).evaluate(
                rule("12005NAME002"), project(event, health, properties)))
                .isEmpty();
    }

    @Test
    void readsJsonNameFromFieldAnnotation() {
        Field field = new Field(
                List.of(new Annotation("JsonProperty", Map.of("value", "\"bad_name\""))),
                AccessModifier.PRIVATE,
                "String",
                "goodName"
        );
        JavaFile dto = javaFile("Payment.java", "com.acme.payments.dto", List.of(), List.of(field));

        assertThat(repository(Map.of()).evaluate(rule("04002DATA002"), project(dto))).hasSize(1);
    }

    @Test
    void appliesImperativeRuleOnlyToCommandChannels() {
        JavaFile sourceFile = javaFile("Channels.java", "com.acme.payments.messaging", List.of(), List.of());
        String source = "String event = \"corridor.core.payments.payment-created.event.topic.v1\";"
                + " String command = \"corridor.core.payments.payment-created.command.queue.v1\";";

        assertThat(repository(Map.of(sourceFile.path(), source))
                .evaluate(rule("07015NAME007"), project(sourceFile)))
                .singleElement();
    }
    @Test
    void urlKebabCaseIgnoresPathVariablesAndWildcards() {
        JavaFile controller = javaFile("PetResource.java", "com.acme.pet.web", List.of(), List.of());
        String source = "@GetMapping(\"/owners/*/pets/{petId}\") void pet() {}";

        assertThat(repository(Map.of(controller.path(), source))
                .evaluate(rule("02004URL001"), project(controller))).isEmpty();
    }

    @Test
    void urlKebabCaseStillRejectsCamelCaseLiteralSegments() {
        JavaFile controller = javaFile("PetResource.java", "com.acme.pet.web", List.of(), List.of());
        String source = "@GetMapping(\"/petTypes\") void types() {}";
        assertThat(repository(Map.of(controller.path(), source))
                .evaluate(rule("02004URL001"), project(controller))).hasSize(1);
    }

    @Test
    void urlKebabCaseIgnoresNonPathAttributesInMultilineMapping() {
        JavaFile controller = javaFile("HotelController.java", "com.acme.hotel.web",
                List.of(new Annotation("RestController", Map.of())), List.of());
        String source = """
                @RequestMapping(value = "",
                        method = RequestMethod.POST,
                        consumes = {"application/json", "application/xml"},
                        produces = {"application/json", "application/xml"})
                void createHotel() {}
                """;

        assertThat(repository(Map.of(controller.path(), source))
                .evaluate(rule("02004URL001"), project(controller)))
                .isEmpty();
    }

    private NamingRuleRepository repository(Map<String, String> sources) {
        SourceTextProvider provider = new SourceTextProvider() {
            public String read(JavaFile javaFile) { return sources.getOrDefault(javaFile.path(), ""); }
            public Map<String, String> readAll(PackageNode project) { return sources; }
        };
        return new NamingRuleRepository(provider, new NamingProjectInspector(provider));
    }

    private ArchitectureRule rule(String id) {
        return new ArchitectureRule(id, "naming rule", RuleType.DETERMINISTIC,
                new RuleLocalMetadata("NAME"), null);
    }

    private PackageNode project(JavaFile... files) {
        return new PackageNode("", "", List.of(), List.of(files));
    }

    private JavaFile javaFile(
            String fileName,
            String packageName,
            List<Annotation> annotations,
            List<Field> fields) {
        return new JavaFile(fileName, "/project/" + fileName, packageName, List.of(),
                ClassType.CLASS, annotations, null, List.of(), fields, List.of(), List.of(), 1, 1);
    }
}
