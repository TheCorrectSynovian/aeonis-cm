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
 * OpenAI API provider implementation.
 * Uses the Chat Completions API: POST /v1/chat/completions
 */
public class OpenAiProvider implements LlmProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm");
    private static final Gson GSON = new Gson();
    
    private final HttpClient httpClient;
    private final String baseUrl;
    
    public OpenAiProvider() {
        this(LlmProviderType.OPENAI.getBaseUrl());
    }
    
    public OpenAiProvider(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    @Override
    public LlmProviderType getType() {
        return LlmProviderType.OPENAI;
    }
    
    @Override
    public CompletableFuture<LlmResponse> chat(List<ChatMessage> messages, Config config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", config.model());
                requestBody.addProperty("temperature", config.temperature());
                requestBody.addProperty("max_tokens", config.maxTokens());
                
                JsonArray messagesArray = new JsonArray();
                for (ChatMessage msg : messages) {
                    JsonObject msgObj = new JsonObject();
                    msgObj.addProperty("role", msg.role());
                    msgObj.addProperty("content", msg.content());
                    messagesArray.add(msgObj);
                }
                requestBody.add("messages", messagesArray);
                
                String jsonBody = GSON.toJson(requestBody);
                
                LOGGER.debug("OpenAI request to model: {}", config.model());
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    LOGGER.warn("OpenAI API error: {} - {}", response.statusCode(), response.body());
                    return LlmResponse.error("API error: " + response.statusCode() + " - " + parseErrorMessage(response.body()));
                }
                
                // Parse response
                JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray choices = responseJson.getAsJsonArray("choices");
                
                if (choices == null || choices.isEmpty()) {
                    return LlmResponse.error("No response from model");
                }
                
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                String content = message.get("content").getAsString();
                
                // Get token usage if available
                int tokensUsed = 0;
                if (responseJson.has("usage")) {
                    tokensUsed = responseJson.getAsJsonObject("usage").get("total_tokens").getAsInt();
                }
                
                LOGGER.debug("OpenAI response received, tokens: {}", tokensUsed);
                return LlmResponse.success(content, tokensUsed);
                
            } catch (Exception e) {
                LOGGER.error("OpenAI provider error: {}", e.getMessage());
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
