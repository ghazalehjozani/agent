package com.example.ai01.agent;

import com.example.ai01.tools.MarkDownFileReader;
import com.example.ai01.tools.ReadFileTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

public class AgentConfiguration {
    @Bean
    StructuralAgent structuralAgent(ChatModel chatModel,
                                    MarkDownFileReader markDownFileReader) {
        return AiServices.builder(StructuralAgent.class)
                .tools(markDownFileReader)
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