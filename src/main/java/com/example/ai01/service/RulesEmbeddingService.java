package com.example.ai01.service;


import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.vector.EmbeddedRule;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private Executor executor;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            @Qualifier("embeddingExecutor") Executor executor) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.executor = executor;
    }

    /**
     * this is how to generate
     * embedding rules
     *
     * @param rule
     * @return
     */


    public void store(List<ArchitectureRule> rules) {

        var embeddedRules =
                rules
                        .stream()
                        .map(this::embedRule)
                        .toList();


        var ids = embeddedRules
                .stream()
                .map((EmbeddedRule::ruleId))
                .toList();

        var segments = embeddedRules
                .stream()
                .map(EmbeddedRule::textSegment)
                .toList();

        var embeddings = embeddedRules
                .stream()
                .map(EmbeddedRule::embedding)
                .toList();

        embeddingStore
                .addAll(ids,
                        embeddings,
                        segments);
    }

    private EmbeddedRule embedRule(ArchitectureRule rule) {

        var semantic = rule.semanticMetadata();

        String embeddingText = """
                Architecture Rule:
                %s

                Semantic Summary:
                %s

                Scope:
                %s

                Applies To:
                %s

                Source Elements:
                %s

                Target Elements:
                %s

                Source Layers:
                %s

                Target Layers:
                %s

                Relations:
                %s

                Languages:
                %s

                Technologies:
                %s

                Protocols:
                %s

                Standards:
                %s

                Communication Contexts:
                %s

                HTTP Methods:
                %s

                HTTP Components:
                %s

                Messaging Elements:
                %s

                Environments:
                %s

                Architectural Concern:
                %s

                Semantic Group:
                %s

                Concepts:
                %s

                Keywords:
                %s

                Applicability:
                %s

                Applicability Conditions:
                %s
                """.formatted(
                rule.description(),
                semantic.semanticSummary(),

                semantic.scope(),

                join(semantic.appliesTo()),

                join(semantic.sourceElements()),
                join(semantic.targetElements()),

                join(semantic.sourceLayers()),
                join(semantic.targetLayers()),

                join(semantic.relations()),

                join(semantic.languages()),

                join(semantic.technologies()),
                join(semantic.protocols()),
                join(semantic.standards()),

                join(semantic.communicationContexts()),

                join(semantic.httpMethods()),
                join(semantic.httpComponents()),

                join(semantic.messagingElements()),

                join(semantic.environments()),

                semantic.concern(),
                semantic.semanticGroup(),

                join(semantic.concepts()),
                join(semantic.keywords()),

                semantic.applicability(),
                join(semantic.applicabilityConditions())
        );

        var textSegment = TextSegment.from(embeddingText);

        var embeddedModel = embeddingModel.embed(textSegment);


        return new EmbeddedRule(
                rule.id(),
                embeddedModel.content(),
                textSegment
        );

    }


    private String join(Set<?> values) {

        if (values == null || values.isEmpty()) {
            return "";
        }

        return values.stream()
                .map(String::valueOf)
                .sorted()
                .collect(Collectors.joining(", "));
    }


}
