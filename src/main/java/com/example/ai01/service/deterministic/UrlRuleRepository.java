package com.example.ai01.service.deterministic;
import com.example.ai01.agent.model.*;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import org.springframework.stereotype.Component;
import java.util.*;
@Component public class UrlRuleRepository extends AbstractRuleRepository {
 public UrlRuleRepository(SourceTextProvider p){super(p);} public String strategyKey(){return "URL";} public boolean supports(String c){return RuleCode.belongsTo(c,"URL");}
 public List<ViolationFinding> evaluate(ArchitectureRule r,PackageNode p){if(!RuleCode.normalize(r.id()).equals("02007URL004"))return evaluateSourceContract(r,p);List<ViolationFinding> out=new ArrayList<>();for(JavaFile f:files(p)){if(!(inLayer(f,"controller")||hasAnnotation(f,"Controller")||hasAnnotation(f,"RestController")))continue;String cp=path(f.annotations());String source=sourceTextProvider.read(f);for(Method m:f.methods()){String mp=path(m.annotations());if(mp==null)continue;String full=((cp==null?"":cp)+"/"+mp).replaceAll("/+","/");if(full.matches("^/?api(?:/.*)?$"))out.add(finding(r,f.path(),SourceLineResolver.methodDeclaration(source,m.name()),"Endpoint "+m.name()+" uses forbidden api/ prefix: "+full,"Remove the api/ prefix from the controller mapping."));}}return List.copyOf(out);}
 private String path(List<Annotation> as){for(Annotation a:as)if(a.name().endsWith("Mapping")){Map<String,String>m=a.attributes()==null?Map.of():a.attributes();return clean(m.getOrDefault("path",m.getOrDefault("value","")));}return null;}
 private String clean(String v){return v==null?"":v.replaceAll("[\\\"'{}]","").trim();}
}
