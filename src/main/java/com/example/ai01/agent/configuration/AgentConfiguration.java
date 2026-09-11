package com.example.ai01.agent.configuration;

import ai.djl.training.loss.Loss;
import com.example.ai01.agent.JsonReportGeneratorAgent;
import com.example.ai01.agent.RuleCheckAgent;
import com.example.ai01.agent.StructuralAgent;
import com.example.ai01.agent.TextReportGeneratorAgent;
import com.example.ai01.monitoring.OpenTelemetryChatModelListener;
import com.example.ai01.tools.ReadFileTool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class AgentConfiguration {
    @Bean
    @Primary
    ChatModel deepSeekChatModel(
            @Value("${DEEPSEEK_API_KEY}") String apiKey,
            ChatModelListener OpenTelemetryChatModelListener) {

        return OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .temperature(0.0)
                .responseFormat("json_object")
                .customParameters(Map.of("thinking", Map.of("type", "disabled")))
                .listeners(List.of(OpenTelemetryChatModelListener))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .timeout(Duration.of(60 * 5, ChronoUnit.SECONDS))
                .build();
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Primary
    @Bean("ruleEmbeddingStore")
    public EmbeddingStore<TextSegment> ruleEmbeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host("pgvector")
                .port(5432)
                .database("architecture")
                .user("architecture")
                .password("architecture")
                .table("rule_embeddings")
                .dimension(384)  // According to AllMiniLmL6V2
                .createTable(true) // create table if there is no table exist
                .build();
    }

    @Bean("projectEmbeddingStore")
    public EmbeddingStore<TextSegment> projectEmbeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host("pgvector")
                .port(5432)
                .database("architecture")
                .user("architecture")
                .password("architecture")
                .table("project_embeddings")
                .dimension(384)  // According to AllMiniLmL6V2
                .createTable(true) // create table if there is no table exist
                .build();
    }

    @Bean
    StructuralAgent structuralAgent(ChatModel chatModel) {
        return AiServices.builder(StructuralAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    RuleCheckAgent ruleCheckAgent(ChatModel chatModel,
                                  ReadFileTool readFileTool) {
        return AiServices.builder(RuleCheckAgent.class)
                .chatModel(chatModel)
                .tools(readFileTool)
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.workspace.report",
            name = "is-json",
            havingValue = "false"
    )
    TextReportGeneratorAgent textReportGeneratorAgent(ChatModel chatModel) {
        return AiServices.builder(TextReportGeneratorAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.report",
            name = "is-json",
            havingValue = "true"
    )
    JsonReportGeneratorAgent jsonReportGeneratorAgent(ChatModel chatModel) {
        return AiServices.builder(JsonReportGeneratorAgent.class)
                .chatModel(chatModel)
                .build();
    }

}