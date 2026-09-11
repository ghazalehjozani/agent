package com.example.ai01.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenTelemetryChatModelListener implements ChatModelListener {

    private static final Object PARENT_SPAN_KEY = new Object();

    private final ObjectMapper objectMapper;

    public OpenTelemetryChatModelListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        Span parentSpan = Span.current();

        requestContext.attributes().put(
                PARENT_SPAN_KEY,
                parentSpan
        );

        parentSpan.setAttribute(
                "input.value",
                toJson(requestContext.chatRequest().messages())
        );
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Span parentSpan = removeParentSpan(
                responseContext.attributes()
        );

        if (parentSpan == null) {
            return;
        }

        parentSpan.setAttribute(
                "output.value",
                toJson(responseContext.chatResponse().aiMessage())
        );

        setTokenUsage(
                parentSpan,
                responseContext.chatResponse().tokenUsage()
        );
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Span parentSpan = removeParentSpan(
                errorContext.attributes()
        );

        if (parentSpan == null) {
            return;
        }

        Throwable error = errorContext.error();
        if (error != null) {
            parentSpan.recordException(error);
            parentSpan.setStatus(StatusCode.ERROR);
        }
    }

    private void setTokenUsage(Span span, TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return;
        }

        Integer inputTokens = tokenUsage.inputTokenCount();
        Integer outputTokens = tokenUsage.outputTokenCount();
        Integer totalTokens = tokenUsage.totalTokenCount();

        if (inputTokens != null) {
            span.setAttribute(
                    "llm.token_count.prompt",
                    inputTokens.longValue()
            );
        }

        if (outputTokens != null) {
            span.setAttribute(
                    "llm.token_count.completion",
                    outputTokens.longValue()
            );
        }

        if (totalTokens != null) {
            span.setAttribute(
                    "llm.token_count.total",
                    totalTokens.longValue()
            );
            return;
        }

        if (inputTokens != null || outputTokens != null) {
            long calculatedTotal =
                    (inputTokens == null ? 0L : inputTokens.longValue())
                            + (outputTokens == null ? 0L : outputTokens.longValue());

            span.setAttribute(
                    "llm.token_count.total",
                    calculatedTotal
            );
        }
    }

    private Span removeParentSpan(Map<Object, Object> attributes) {
        Object value = attributes.remove(PARENT_SPAN_KEY);
        return value instanceof Span span
                ? span
                : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }
}