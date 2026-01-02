package com.qc.aeonis.llm.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Google Gemini API provider implementation.
 * Uses the generateContent API endpoint with API key authentication.
 * 
 * Note: Gemini uses a different message format than OpenAI.
 * - "user" role stays as "user"
 * - "assistant" role becomes "model"
 * - System prompts are handled via systemInstruction
 */
public class GeminiProvider implements LlmProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm");
    private static final Gson GSON = new Gson();
    
    private final HttpClient httpClient;
    private final String baseUrl;
    
    public GeminiProvider() {
        this(LlmProviderType.GEMINI.getBaseUrl());
    }
    
    public GeminiProvider(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    @Override
    public LlmProviderType getType() {
        return LlmProviderType.GEMINI;
    }
    
    @Override
    public CompletableFuture<LlmResponse> chat(List<ChatMessage> messages, Config config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build request body for Gemini format
                JsonObject requestBody = new JsonObject();
                
                // Generation config
                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("temperature", config.temperature());
                generationConfig.addProperty("maxOutputTokens", config.maxTokens());
                requestBody.add("generationConfig", generationConfig);
                
                // Convert messages to Gemini format
                JsonArray contents = new JsonArray();
                String systemPrompt = null;
                
                for (ChatMessage msg : messages) {
                    if ("system".equals(msg.role())) {
                        // Extract system prompt for systemInstruction
                        systemPrompt = msg.content();
                        continue;
                    }
                    
                    JsonObject content = new JsonObject();
                    // Gemini uses "user" and "model" roles
                    String role = "assistant".equals(msg.role()) ? "model" : msg.role();
                    content.addProperty("role", role);
                    
                    JsonArray parts = new JsonArray();
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("text", msg.content());
                    parts.add(textPart);
                    content.add("parts", parts);
                    
                    contents.add(content);
                }
                
                requestBody.add("contents", contents);
                
                // Add system instruction if present
                if (systemPrompt != null) {
                    JsonObject systemInstruction = new JsonObject();
                    JsonArray parts = new JsonArray();
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("text", systemPrompt);
                    parts.add(textPart);
                    systemInstruction.add("parts", parts);
                    requestBody.add("systemInstruction", systemInstruction);
                }
                
                String jsonBody = GSON.toJson(requestBody);
                
                // Gemini uses URL parameter for API key
                String url = String.format("%s/models/%s:generateContent?key=%s",
                    baseUrl, config.model(), config.apiKey());
                
                LOGGER.debug("Gemini request to model: {}", config.model());
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    LOGGER.warn("Gemini API error: {} - {}", response.statusCode(), response.body());
                    return LlmResponse.error("API error: " + response.statusCode() + " - " + parseErrorMessage(response.body()));
                }
                
                // Parse response
                JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                
                // Check for candidates
                if (!responseJson.has("candidates")) {
                    return LlmResponse.error("No response from model");
                }
                
                JsonArray candidates = responseJson.getAsJsonArray("candidates");
                if (candidates.isEmpty()) {
                    return LlmResponse.error("Empty response from model");
                }
                
                JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                JsonObject content = firstCandidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                
                StringBuilder contentBuilder = new StringBuilder();
                for (int i = 0; i < parts.size(); i++) {
                    JsonObject part = parts.get(i).getAsJsonObject();
                    if (part.has("text")) {
                        contentBuilder.append(part.get("text").getAsString());
                    }
                }
                
                // Get token count if available
                int tokensUsed = 0;
                if (responseJson.has("usageMetadata")) {
                    JsonObject usage = responseJson.getAsJsonObject("usageMetadata");
                    if (usage.has("totalTokenCount")) {
                        tokensUsed = usage.get("totalTokenCount").getAsInt();
                    }
                }
                
                LOGGER.debug("Gemini response received, tokens: {}", tokensUsed);
                return LlmResponse.success(contentBuilder.toString(), tokensUsed);
                
            } catch (Exception e) {
                LOGGER.error("Gemini provider error: {}", e.getMessage());
                return LlmResponse.error("Connection error: " + e.getMessage());
            }
        });
    }
    
    private String parseErrorMessage(String responseBody) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            if (json.has("error")) {
                JsonObject error = json.getAsJsonObject("error");
                return error.has("message") ? error.get("message").getAsString() : "Unknown error";
            }
        } catch (Exception ignored) {}
        return responseBody.substring(0, Math.min(responseBody.length(), 100));
    }
}
