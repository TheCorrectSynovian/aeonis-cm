package com.qc.aeonis.llm.provider;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for LLM providers.
 * All implementations should handle API calls asynchronously to avoid blocking the server thread.
 */
public interface LlmProvider {
    
    /**
     * Message record for chat completions
     */
    record ChatMessage(String role, String content) {}
    
    /**
     * Configuration for the provider
     */
    record Config(
        String apiKey,
        String model,
        double temperature,
        int maxTokens
    ) {
        public static Config withDefaults(String apiKey, String model) {
            return new Config(apiKey, model, 0.7, 1024);
        }
    }
    
    /**
     * Response from the LLM
     */
    record LlmResponse(
        boolean success,
        String content,
        String errorMessage,
        int tokensUsed
    ) {
        public static LlmResponse success(String content, int tokens) {
            return new LlmResponse(true, content, null, tokens);
        }
        
        public static LlmResponse error(String message) {
            return new LlmResponse(false, null, message, 0);
        }
    }
    
    /**
     * Get the provider type
     */
    LlmProviderType getType();
    
    /**
     * Send a chat completion request asynchronously.
     * 
     * @param messages List of messages in the conversation
     * @param config Provider configuration (key, model, temperature, etc.)
     * @return CompletableFuture containing the response
     */
    CompletableFuture<LlmResponse> chat(List<ChatMessage> messages, Config config);
    
    /**
     * Test the connection with a simple prompt.
     * 
     * @param config Provider configuration
     * @return CompletableFuture containing success/failure
     */
    default CompletableFuture<LlmResponse> testConnection(Config config) {
        return chat(List.of(new ChatMessage("user", "Say 'Hello' in one word.")), config);
    }
    
    /**
     * Redact API key for logging (show only first 4 and last 4 characters)
     */
    static String redactApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
