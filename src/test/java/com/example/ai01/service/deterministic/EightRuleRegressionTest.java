package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.*;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleLocalMetadata;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class EightRuleRegressionTest {
    @Test void pageableIsValidPagination() {
        JavaFile c = file("OrderController.java", "com.acme.shop.controller", List.of(endpoint("list", List.of(new Parameter("Pageable", "pageable")))), List.of(), List.of());
        assertThat(new ApiRuleRepository(provider(Map.of())).evaluate(rule("02016API005", "API"), project(c))).isEmpty();
    }

    @Test void fieldsAndExpandAreDetectedOnEndpoints() {
        JavaFile c = file("OrderController.java", "com.acme.shop.controller", List.of(
                endpoint("fields", List.of(new Parameter("String", "fields"))),
                endpoint("expand", List.of(new Parameter("String", "expand")))), List.of(), List.of());
        ApiRuleRepository repository = new ApiRuleRepository(provider(Map.of()));
        assertThat(repository.evaluate(rule("06031API001", "API"), project(c))).hasSize(1);
        assertThat(repository.evaluate(rule("06032API002", "API"), project(c))).hasSize(1);
    }

    @Test void labelRuleRequiresControlFlow() {
        JavaFile dto = file("Status.java", "com.acme.shop.dto", List.of(), List.of(), List.of());
        assertThat(new DataRuleRepository(provider(Map.of(dto.path(), "String label = status.getLabel();")))
                .evaluate(rule("04038DATA005", "DATA"), project(dto))).isEmpty();
        assertThat(new DataRuleRepository(provider(Map.of(dto.path(), "if (status.getLabel().equals(\"active\")) run();")))
                .evaluate(rule("04038DATA005", "DATA"), project(dto))).hasSize(1);
    }

    @Test void nullableExampleRuleTargetsOnlyOptionalNullableSchemaFields() {
        Field ordinary = new Field(List.of(), AccessModifier.PRIVATE, "String", "name");
        Field invalid = new Field(List.of(new Annotation("Schema", Map.of("nullable", "true", "required", "false"))), AccessModifier.PRIVATE, "String", "nickname");
        Field valid = new Field(List.of(new Annotation("Schema", Map.of("nullable", "true", "required", "false", "example", "null"))), AccessModifier.PRIVATE, "String", "alias");
        JavaFile dto = file("Customer.java", "com.acme.shop.dto", List.of(), List.of(ordinary, invalid, valid), List.of());
        assertThat(new DataRuleRepository(provider(Map.of())).evaluate(rule("04043DATA002", "DATA"), project(dto))).hasSize(1);
    }

    @Test void staticFieldsAreNotJsonProperties() {
        Field constant = new Field(List.of(), AccessModifier.PRIVATE, "String", "KEY_PREFIX");
        JavaFile dto = file("Order.java", "com.acme.shop.dto", List.of(), List.of(constant), List.of());
        String source = "class Order { private static final String KEY_PREFIX = \"order\"; }";
        assertThat(naming(Map.of(dto.path(), source)).evaluate(rule("04002DATA002", "NAME"), project(dto))).isEmpty();
    }

    @Test void authorizationRuleIgnoresMessagesAndChecksHeaderCalls() {
        JavaFile f = file("Security.java", "com.acme.shop.security", List.of(), List.of(), List.of());
        assertThat(naming(Map.of(f.path(), "log.warn(\"Missing Authorization header\");"))
                .evaluate(rule("08009SECURITY008", "NAME"), project(f))).isEmpty();
        assertThat(naming(Map.of(f.path(), "request.getHeader(\"authorization\");"))
                .evaluate(rule("08009SECURITY008", "NAME"), project(f))).hasSize(1);
    }

    @Test void springBootApplicationMayUseProjectRootPackage() {
        JavaFile app = file("ShopApplication.java", "com.acme.shop", List.of(), List.of(), List.of(new Annotation("SpringBootApplication", Map.of())));
        assertThat(new PackageRuleRepository(provider(Map.of())).evaluate(rule("12011PKG001", "PKG"), project(app))).isEmpty();
    }

    @Test void urlViolationContainsEndpointSourceLine() {
        Method endpoint = endpoint("listOrders", List.of());
        JavaFile controller = file("OrderController.java", "com.acme.shop.controller", List.of(endpoint), List.of(),
                List.of(new Annotation("RequestMapping", Map.of("value", "\"/api/v1/orders\""))));
        String source = "class OrderController {\n  public Object listOrders() { return null; }\n}";
        assertThat(new UrlRuleRepository(provider(Map.of(controller.path(), source)))
                .evaluate(rule("02007URL004", "URL"), project(controller)))
                .singleElement().satisfies(item -> assertThat(item.line()).isEqualTo(2));
    }
    private NamingRuleRepository naming(Map<String,String> sources) { SourceTextProvider p=provider(sources); return new NamingRuleRepository(p,new NamingProjectInspector(p)); }
    private Method endpoint(String name,List<Parameter> parameters){return new Method(name,AccessModifier.PUBLIC,List.of(new Annotation("GetMapping",Map.of())),"Object",parameters,false);}
    private JavaFile file(String name,String pkg,List<Method> methods,List<Field> fields,List<Annotation> annotations){return new JavaFile(name,"/project/"+name,pkg,List.of(),ClassType.CLASS,annotations,null,List.of(),fields,List.of(),methods,1,1);}
    private PackageNode project(JavaFile... files){return new PackageNode("","",List.of(),List.of(files));}
    private ArchitectureRule rule(String id,String key){return new ArchitectureRule(id,"rule",RuleType.DETERMINISTIC,new RuleLocalMetadata(key),null);}
    private SourceTextProvider provider(Map<String,String> sources){return new SourceTextProvider(){public String read(JavaFile f){return sources.getOrDefault(f.path(),"");}public Map<String,String> readAll(PackageNode p){return sources;}};}
}
