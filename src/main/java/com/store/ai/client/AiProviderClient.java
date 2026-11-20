package com.store.ai.client;

public interface AiProviderClient {
    String chat(String systemPrompt, String userPrompt);
}
