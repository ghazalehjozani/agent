package com.example.ai01.agent.model.ruleextraction;

public enum RuleApplicability {

    /**
     * The rule applies whenever its architectural scope exists.
     */
    ALWAYS,

    /**
     * The rule applies only when one or more explicit conditions
     * represented by applicabilityConditions are satisfied.
     */
    CONDITIONAL
}