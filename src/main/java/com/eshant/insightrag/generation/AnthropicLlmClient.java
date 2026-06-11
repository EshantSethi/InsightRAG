package com.eshant.insightrag.generation;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Real-LLM {@link LlmClient} backed by Anthropic's Claude via LangChain4j. Constructed only when an
 * API key is configured (see {@code AppConfig}); otherwise the project uses {@link MockLlmClient}.
 *
 * <p>Temperature defaults to 0 for reproducible, grounded answers — desirable for documentation QA
 * and for stable eval numbers.
 */
public class AnthropicLlmClient implements LlmClient {

    private final ChatLanguageModel model;
    private final String modelName;

    public AnthropicLlmClient(String apiKey, String modelName, double temperature, int maxTokens) {
        this.modelName = modelName;
        this.model = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Override
    public String generate(String prompt) {
        return model.generate(prompt);
    }

    @Override
    public String name() {
        return "anthropic:" + modelName;
    }

    @Override
    public boolean isMock() {
        return false;
    }
}
