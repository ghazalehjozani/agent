package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleLocalMetadata;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RuleRoutingContractTest {
    private final RuleStrategyRegistry registry = new RuleStrategyRegistry(List.of(new LayerStub()));

    @Test void semanticRuleIsNeverSupportedLocallyEvenWithAKey() {
        assertThat(registry.supports(rule(RuleType.SEMANTIC, "LAYER"))).isFalse();
    }

    @Test void deterministicRuleWithoutKeyIsSentAwayFromLocalPath() {
        assertThat(registry.supports(rule(RuleType.DETERMINISTIC, null))).isFalse();
    }

    @Test void deterministicRuleWithMatchingRegisteredKeyIsSupportedLocally() {
        assertThat(registry.supports(rule(RuleType.DETERMINISTIC, "LAYER"))).isTrue();
    }

    @Test void deterministicRuleWithUnknownKeyIsNotSupportedLocally() {
        assertThat(registry.supports(rule(RuleType.DETERMINISTIC, "UNKNOWN"))).isFalse();
    }

    private ArchitectureRule rule(RuleType type, String key) {
        return new ArchitectureRule("12001LAYER001", "rule", type, new RuleLocalMetadata(key), null);
    }

    private static class LayerStub implements DeterministicRuleRepository {
        public String strategyKey() { return "LAYER"; }
        public boolean supports(String code) { return RuleCode.belongsTo(code, "LAYER"); }
        public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) { return List.of(); }
    }
}
