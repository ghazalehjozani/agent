package com.example.ai01.service;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.RuleSemanticMetadata;
import com.example.ai01.agent.model.vector.EmbeddedRule;
import com.example.ai01.monitoring.TraceOperation;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class RulesEmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Executor embeddingExecutor;

    public RulesEmbeddingService(
            EmbeddingModel embeddingModel,
            @Qualifier("ruleEmbeddingStore")
            EmbeddingStore<TextSegment> embeddingStore,
            @Qualifier("embeddingExecutor")
            Executor embeddingExecutor) {

        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.embeddingExecutor = embeddingExecutor;
    }

    /**
     * Async entry point.
     * <p>
     * این متد زمانی مناسب است که Rule Embedding و
     * Project Embedding را از Facade به صورت موازی اجرا کنیم.
     */
    @TraceOperation(serviceName = "rules-embedding", spanName = "embedding.rules.store", newSpan = true , spanKind = "EMBEDDING")
    public CompletableFuture<List<EmbeddedRule>> storeAsync(List<ArchitectureRule> rules) {
        return CompletableFuture.supplyAsync(() -> store(rules), embeddingExecutor);
    }

    public List<EmbeddedRule> store(List<ArchitectureRule> rules) {

        embeddingStore.removeAll(
                metadataKey("documentType")
                        .isEqualTo("ARCHITECTURE_RULE")
        );

        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<TextSegment> segments = rules.stream()
                .map(this::createSegment)
                .toList();

        List<Embedding> embeddings =
                embeddingModel
                        .embedAll(segments)
                        .content();

        List<String> ids = rules.stream()
                .map(rule -> createEmbeddingId(rule.id()))
                .toList();

        embeddingStore.addAll(
                ids,
                embeddings,
                segments
        );

        return IntStream
                .range(0, rules.size())
                .mapToObj(i ->
                        new EmbeddedRule(
                                rules.get(i).id(),
                                embeddings.get(i),
                                segments.get(i)
                        )
                )
                .toList();
    }


    /**
     * @param ruleId
     * @return
     */
    private String createEmbeddingId(String ruleId) {

        return UUID.nameUUIDFromBytes(
                ("ARCHITECTURE_RULE:" + ruleId)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }


    private TextSegment createSegment(
            ArchitectureRule rule) {

        RuleSemanticMetadata semantic =
                rule.semanticMetadata();


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

                safe(rule.description()),
                safe(semantic.semanticSummary()),

                safe(semantic.scope()),

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

                safe(semantic.concern()),
                safe(semantic.semanticGroup()),

                join(semantic.concepts()),
                join(semantic.keywords()),

                safe(semantic.applicability()),
                join(semantic.applicabilityConditions())
        );


        /*
         * این Metadata وارد Vector نمی‌شود.
         *
         * برای:
         * - filtering
         * - routing
         * - پیدا کردن Rule اصلی
         * - پیدا کردن deterministic strategy
         *
         * استفاده می‌شود.
         */
        Metadata metadata = new Metadata();

        metadata.put(
                "documentType",
                "ARCHITECTURE_RULE"
        );

        metadata.put(
                "ruleId",
                safe(rule.id())
        );

        metadata.put(
                "ruleType",
                safe(rule.ruleType())
        );

        metadata.put(
                "scope",
                safe(semantic.scope())
        );

        metadata.put(
                "appliesTo",
                join(semantic.appliesTo())
        );

        metadata.put(
                "sourceElements",
                join(semantic.sourceElements())
        );

        metadata.put(
                "targetElements",
                join(semantic.targetElements())
        );

        metadata.put(
                "sourceLayers",
                join(semantic.sourceLayers())
        );

        metadata.put(
                "targetLayers",
                join(semantic.targetLayers())
        );

        metadata.put(
                "technologies",
                join(semantic.technologies())
        );

        metadata.put(
                "protocols",
                join(semantic.protocols())
        );

        metadata.put(
                "semanticGroup",
                safe(semantic.semanticGroup())
        );

        metadata.put(
                "concern",
                safe(semantic.concern())
        );

        metadata.put(
                "applicability",
                safe(semantic.applicability())
        );

        metadata.put(
                "requiredContext",
                join(semantic.requiredContext())
        );

        metadata.put(
                "requiredArtifacts",
                join(semantic.requiredArtifacts())
        );


        if (rule.localMetadata() != null
                && rule.localMetadata().strategyKey() != null) {

            metadata.put(
                    "strategyKey",
                    rule.localMetadata().strategyKey()
            );
        }


        return TextSegment.from(
                embeddingText,
                metadata
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


    private String safe(Object value) {

        return value == null
                ? ""
                : String.valueOf(value);
    }
}