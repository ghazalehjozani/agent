package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.ClassType;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleLocalMetadata;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class StructuralRuleRepositoriesTest {
    @Test void layer001RejectsControllerCallingRepository() {
        JavaFile a=file("PaymentController.java","com.acme.pay.controller",List.of("com.acme.pay.repository.PaymentRepository"),List.of());
        JavaFile b=file("PaymentRepository.java","com.acme.pay.repository",List.of(),List.of());
        assertThat(new LayerRuleRepository(provider(Map.of(a.path(),"class PaymentController { PaymentRepository r; }"))).evaluate(rule("12001LAYER001","LAYER"),project(a,b))).hasSize(1);
    }
    @Test void layer002AllowsServiceCallingRepository() {
        JavaFile a=file("PaymentService.java","com.acme.pay.service",List.of("com.acme.pay.repository.PaymentRepository"),List.of());
        JavaFile b=file("PaymentRepository.java","com.acme.pay.repository",List.of(),List.of());
        assertThat(new LayerRuleRepository(provider(Map.of(a.path(),"class PaymentService { PaymentRepository r; }"))).evaluate(rule("12002LAYER002","LAYER"),project(a,b))).isEmpty();
    }
    @Test void layer003RejectsRepositoryDependingOnService() {
        JavaFile a=file("PaymentRepository.java","com.acme.pay.repository",List.of("com.acme.pay.service.PaymentService"),List.of());
        JavaFile b=file("PaymentService.java","com.acme.pay.service",List.of(),List.of());
        assertThat(new LayerRuleRepository(provider(Map.of(a.path(),"class PaymentRepository { PaymentService s; }"))).evaluate(rule("12003LAYER003","LAYER"),project(a,b))).hasSize(1);
    }
    @Test void pattern001RequiresAdviceHandlerForThrownException() {
        JavaFile a=file("PaymentController.java","com.acme.pay.controller",List.of(),List.of(new Annotation("RestController",Map.of())));
        JavaFile b=file("ErrorAdvice.java","com.acme.pay.exception",List.of(),List.of(new Annotation("ControllerAdvice",Map.of())));
        Map<String,String> sources=Map.of(a.path(),"throw new PaymentException();",b.path(),"@ControllerAdvice class ErrorAdvice { @ExceptionHandler(OtherException.class) void h(){} }");
        assertThat(new PatternRuleRepository(provider(sources)).evaluate(rule("12009PATTERN001","PATTERN"),project(a,b))).hasSize(1);
    }
    @Test void pattern001IgnoresExceptionsOutsideHttpBoundary() {
        JavaFile service=file("JsonService.java","com.acme.pay.service",List.of(),List.of());
        Map<String,String> sources=Map.of(service.path(),"void parse() throws IOException { throw new JacksonException(); }");
        assertThat(new PatternRuleRepository(provider(sources)).evaluate(rule("12009PATTERN001","PATTERN"),project(service))).isEmpty();
    }
    @Test void pattern002RequiresEntityAndTable() {
        JavaFile a=file("Payment.java","com.acme.pay.entity",List.of(),List.of(new Annotation("Entity",Map.of())));
        assertThat(new PatternRuleRepository(provider(Map.of())).evaluate(rule("12010PATTERN002","PATTERN"),project(a))).hasSize(1);
    }
    @Test void pkg001ChecksDeclarationAndSourcePathAlignment() {
        JavaFile a=fileAt("PaymentService.java","C:/repo/src/main/java/com/acme/pay/service/PaymentService.java","com.acme.pay.service");
        JavaFile b=fileAt("Payment.java","C:/repo/src/main/java/com/acme/pay/model/Payment.java","com.acme.pay.dto");
        assertThat(new PackageRuleRepository(provider(Map.of())).evaluate(rule("12011PKG001","PKG"),project(a,b))).singleElement().satisfies(v->assertThat(v.file()).isEqualTo(b.path()));
    }
    private ArchitectureRule rule(String id,String key){return new ArchitectureRule(id,"rule",RuleType.DETERMINISTIC,new RuleLocalMetadata(key),null);}
    private PackageNode project(JavaFile... files){return new PackageNode("","",List.of(),List.of(files));}
    private JavaFile file(String name,String pkg,List<String> imports,List<Annotation> annotations){return new JavaFile(name,"/project/"+name,pkg,imports,ClassType.CLASS,annotations,null,List.of(),List.of(),List.of(),List.of(),1,1);}
    private JavaFile fileAt(String name,String path,String pkg){return new JavaFile(name,path,pkg,List.of(),ClassType.CLASS,List.of(),null,List.of(),List.of(),List.of(),List.of(),1,1);}
    private SourceTextProvider provider(Map<String,String> sources){return new SourceTextProvider(){public String read(JavaFile f){return sources.getOrDefault(f.path(),"");}public Map<String,String> readAll(PackageNode p){return sources;}};}
}
