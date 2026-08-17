package com.example.ai01.service;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

    private ChatModel chatModel;

    public AiHealthIndicator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Health health() {

        return Health
                .up()
                .withDetail("provider", chatModel.provider().name())
                .build();
    }

}
