package com.qc.aeonis.llm.provider;

import java.util.List;

/**
 * Enum representing supported LLM providers.
 * Each provider has a display name, base URL, and default models.
 */
public enum LlmProviderType {
    OPENAI("OpenAI", "https://api.openai.com/v1", List.of(
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4-turbo",
        "gpt-5",
        "gpt-5.1",
        "gpt-5.2",
        "gpt-4",
        "gpt-3.5-turbo"
    )),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", List.of(
        "gemini-2.0-flash-exp",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-1.0-pro"
    )),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", List.of(
        "openai/gpt-oss-20b",
        "openai/gpt-oss-20b:free",
        "nousresearch/deephermes-3-llama-3-8b-preview:free",
        "mistralai/mistral-small-3.1-24b-instruct:free",
        "anthropic/claude-3.5-sonnet",
        "google/gemini-pro-1.5",
        "meta-llama/llama-3.1-70b-instruct",
        "mistralai/mixtral-8x7b-instruct"
    ));

    private final String displayName;
    private final String baseUrl;
    private final List<String> defaultModels;

    LlmProviderType(String displayName, String baseUrl, List<String> defaultModels) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.defaultModels = defaultModels;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public List<String> getDefaultModels() {
        return defaultModels;
    }

    /**
     * Get provider by name (case-insensitive)
     */
    public static LlmProviderType fromName(String name) {
        for (LlmProviderType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return OPENAI; // Default fallback
    }
}
