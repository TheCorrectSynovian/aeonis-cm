package com.qc.aeonis.llm.provider;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating LLM provider instances.
 * Caches provider instances for reuse.
 */
public class LlmProviderFactory {
    
    private static final Map<LlmProviderType, LlmProvider> PROVIDERS = new HashMap<>();
    
    static {
        // Pre-initialize providers
        PROVIDERS.put(LlmProviderType.OPENAI, new OpenAiProvider());
        PROVIDERS.put(LlmProviderType.GEMINI, new GeminiProvider());
        PROVIDERS.put(LlmProviderType.OPENROUTER, new OpenRouterProvider());
    }
    
    /**
     * Get a provider instance by type.
     */
    public static LlmProvider getProvider(LlmProviderType type) {
        return PROVIDERS.get(type);
    }
    
    /**
     * Get a provider instance by name.
     */
    public static LlmProvider getProvider(String providerName) {
        return getProvider(LlmProviderType.fromName(providerName));
    }
    
    /**
     * Create a new provider with custom base URL (useful for proxies/self-hosted)
     */
    public static LlmProvider createProvider(LlmProviderType type, String customBaseUrl) {
        return switch (type) {
            case OPENAI -> new OpenAiProvider(customBaseUrl);
            case GEMINI -> new GeminiProvider(customBaseUrl);
            case OPENROUTER -> new OpenRouterProvider(customBaseUrl);
        };
    }
}
