package com.example.ai01.agent;


import com.example.ai01.configuration.PathProperties;
import com.example.ai01.tools.MarkDownFileReader;
import com.example.ai01.tools.ProjectFileExtractor;
import com.example.ai01.tools.ReadFileTool;
import dev.langchain4j.model.chat.ChatModel;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

public class AgentConfiguration {

    /*@Bean
    OllamaChatModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen2.5-coder:7b")
                .temperature(0.1)
                .timeout(Duration.ofSeconds(120))
                .maxRetries(1)
                .build();
    }*/

    @Bean
    ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey("sk-a3ad20e356cd4302b8036fa1abd6d1d9")
                .modelName("deepseek-v4-pro")
                .temperature(0.1)
                .timeout(Duration.ofSeconds(600))
                .maxRetries(1)
                .build();
    }

    @Bean
    StructuralAgent structuralAgent(ChatModel chatModel,
                                    MarkDownFileReader markDownFileReader,
                                    ProjectFileExtractor projectFileExtractor) {
        return AiServices.builder(StructuralAgent.class)
                .tools(markDownFileReader, projectFileExtractor)
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