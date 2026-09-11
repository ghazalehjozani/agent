package com.example.ai01.service;

import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.monitoring.TraceOperation;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;

@Service

public class RuleStrategyRegistry {
    private Map<String, Function<ViolationFinding, String>> strategies;
    public RuleStrategyRegistry() {
    }
    public RuleStrategyRegistry(Map<String, Function<ViolationFinding, String>> strategies) {
        this.strategies = strategies;
    }
    @TraceOperation(serviceName = "rule-strategy-registry",  spanName = "" , newSpan = false)
    Function<ViolationFinding, String> getStrategy(String key) {
        return strategies.get(key);
    }
    public synchronized void setStrategy(String key, Function<ViolationFinding, String> strategy) {
        this.strategies.put(key, strategy);
    }
}