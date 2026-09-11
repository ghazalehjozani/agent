package com.example.ai01.service.deterministic;
import com.example.ai01.agent.model.PackageNode; import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule; import org.springframework.stereotype.Component; import java.util.List;
@Component public class SecurityRuleRepository extends AbstractRuleRepository {
 public SecurityRuleRepository(SourceTextProvider p){super(p);} public String strategyKey(){return "SECURITY";}
 public boolean supports(String c){return RuleCode.belongsTo(c,"SEC","SECURITY");}
 public List<ViolationFinding> evaluate(ArchitectureRule r,PackageNode p){return evaluateSourceContract(r,p);}}
