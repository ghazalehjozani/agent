package com.example.ai01.service;

import com.example.ai01.agent.StructuralAgent;
import com.example.ai01.agent.model.RuleContainer;
import com.example.ai01.monitoring.TraceOperation;

import org.springframework.stereotype.Service;

@Service
public class StructuralAgentExecutionService {

    private final StructuralAgent structuralAgent;


    public StructuralAgentExecutionService(
            StructuralAgent structuralAgent
    ) {

        this.structuralAgent =
                structuralAgent;
    }


    @TraceOperation(
            spanName = "agent.rule-extraction",
            serviceName = "structural-agent",
            newSpan = true ,
            spanKind = "AGENT"
    )
    public RuleContainer extract(
            String content
    ) {

        return structuralAgent.extract(
                content
        );
    }
}