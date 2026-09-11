package com.example.ai01.service.deterministic;
import com.example.ai01.agent.model.PackageNode; import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule; import org.springframework.stereotype.Component; import java.util.List;
@Component public class IdempotencyRuleRepository extends AbstractRuleRepository {
 public IdempotencyRuleRepository(SourceTextProvider p){super(p);} public String strategyKey(){return "IDEMPOTENCY";}
 public boolean supports(String c){return RuleCode.belongsTo(c,"IDEMPOTENCY","IDEM");}
 public List<ViolationFinding> evaluate(ArchitectureRule r,PackageNode p){return evaluateSourceContract(r,p);}}
