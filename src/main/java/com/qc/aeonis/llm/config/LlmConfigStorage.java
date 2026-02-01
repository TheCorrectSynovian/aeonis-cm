package com.qc.aeonis.llm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.qc.aeonis.llm.provider.LlmProvider;
import com.qc.aeonis.llm.provider.LlmProviderType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Server-side storage for LLM configuration.
 * 
 * SECURITY NOTES:
 * - API keys are encrypted at rest using AES
 * - Keys are NEVER sent back to clients after initial entry
 * - Keys are NEVER logged in plaintext (only redacted)
 * - Only operators (permission level 2+) can access this config
 * 
 * Storage location: <world>/aeonis/llm_config.json
 */
public class LlmConfigStorage {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_DIR = "aeonis";
    private static final String CONFIG_FILE = "llm_config.json";
    
    // Simple encryption key derived from server instance
    // In production, consider using a more robust key management system
    private static final String ENCRYPTION_SEED = "AeonisLlmConfig2026";
    
    private final MinecraftServer server;
    private final Path configPath;
    
    // Cached config
    private LlmConfig cachedConfig;
    
    public LlmConfigStorage(MinecraftServer server) {
        this.server = server;
        this.configPath = server.getWorldPath(LevelResource.ROOT)
            .resolve(CONFIG_DIR)
            .resolve(CONFIG_FILE);
        
        load();
    }
    
    /**
     * LLM configuration data class
     */
    public static class LlmConfig {
        public String providerType = LlmProviderType.OPENAI.name();
        public String model = "gpt-4o-mini";
        public String encryptedApiKey = "";
        public double temperature = 0.7;
        public int maxTokens = 1024;
        
        // Safety settings
        public int maxBlocksPerMinute = 30;
        public int maxEditRadius = 50;
        public boolean griefProtectionEnabled = true;
        
        // Bot settings
        public boolean botEnabled = false;
        public UUID ownerUuid = null;
        
        /**
         * Check if the config has a valid API key set
         */
        public boolean hasApiKey() {
            return encryptedApiKey != null && !encryptedApiKey.isEmpty();
        }
    }
    
    /**
     * Load configuration from disk
     */
    public void load() {
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                cachedConfig = GSON.fromJson(json, LlmConfig.class);
                LOGGER.info("Loaded LLM config from {}", configPath);
            } else {
                cachedConfig = new LlmConfig();
                LOGGER.info("Created new LLM config");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load LLM config: {}", e.getMessage());
            cachedConfig = new LlmConfig();
        }
    }
    
    /**
     * Save configuration to disk
     */
    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(cachedConfig);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
            LOGGER.info("Saved LLM config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save LLM config: {}", e.getMessage());
        }
    }
    
    /**
     * Get the current configuration
     */
    public LlmConfig getConfig() {
        return cachedConfig;
    }
    
    /**
     * Update configuration with new values
     * 
     * @param providerType The LLM provider type
     * @param model The model to use
     * @param plaintextApiKey The API key (will be encrypted before storage)
     * @param temperature Temperature setting
     * @param maxTokens Max tokens setting
     */
    public void updateConfig(String providerType, String model, String plaintextApiKey, 
                            double temperature, int maxTokens) {
        cachedConfig.providerType = providerType;
        cachedConfig.model = model;
        cachedConfig.temperature = temperature;
        cachedConfig.maxTokens = maxTokens;
        
        // Only update API key if a new one is provided
        if (plaintextApiKey != null && !plaintextApiKey.isEmpty()) {
            cachedConfig.encryptedApiKey = encrypt(plaintextApiKey);
            LOGGER.info("Updated LLM config: provider={}, model={}, key={}", 
                providerType, model, LlmProvider.redactApiKey(plaintextApiKey));
        } else {
            LOGGER.info("Updated LLM config: provider={}, model={} (key unchanged)", 
                providerType, model);
        }
        
        save();
    }
    
    /**
     * Update safety settings
     */
    public void updateSafetySettings(int maxBlocksPerMinute, int maxEditRadius, boolean griefProtection) {
        cachedConfig.maxBlocksPerMinute = maxBlocksPerMinute;
        cachedConfig.maxEditRadius = maxEditRadius;
        cachedConfig.griefProtectionEnabled = griefProtection;
        save();
    }
    
    /**
     * Set bot enabled state
     */
    public void setBotEnabled(boolean enabled, UUID ownerUuid) {
        cachedConfig.botEnabled = enabled;
        cachedConfig.ownerUuid = ownerUuid;
        save();
    }
    
    /**
     * Get the decrypted API key (server-side only!)
     * 
     * WARNING: This should NEVER be sent to clients or logged!
     */
    public String getDecryptedApiKey() {
        if (cachedConfig.encryptedApiKey == null || cachedConfig.encryptedApiKey.isEmpty()) {
            return null;
        }
        return decrypt(cachedConfig.encryptedApiKey);
    }
    
    /**
     * Get a provider config object for making API calls
     */
    public LlmProvider.Config getProviderConfig() {
        String apiKey = getDecryptedApiKey();
        if (apiKey == null) {
            return null;
        }
        return new LlmProvider.Config(
            apiKey,
            cachedConfig.model,
            cachedConfig.temperature,
            cachedConfig.maxTokens
        );
    }
    
    /**
     * Get the provider type
     */
    public LlmProviderType getProviderType() {
        return LlmProviderType.fromName(cachedConfig.providerType);
    }
    
    /**
     * Check if API key is configured
     */
    public boolean hasApiKey() {
        return cachedConfig.hasApiKey();
    }
    
    // ================ ENCRYPTION UTILITIES ================
    
    /**
     * Encrypt a string using AES
     */
    private String encrypt(String plaintext) {
        try {
            SecretKey key = generateKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            LOGGER.error("Encryption failed: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Decrypt a string using AES
     */
    private String decrypt(String ciphertext) {
        try {
            SecretKey key = generateKey();
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Decryption failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate a consistent AES key from the seed
     */
    private SecretKey generateKey() throws Exception {
        // Use server's world UUID as additional entropy if available
        String fullSeed = ENCRYPTION_SEED;
        if (server != null) {
            fullSeed += server.getWorldData().getLevelName();
        }
        
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(fullSeed.getBytes(StandardCharsets.UTF_8));
        // Use first 16 bytes for AES-128
        byte[] aesKey = new byte[16];
        System.arraycopy(keyBytes, 0, aesKey, 0, 16);
        return new SecretKeySpec(aesKey, "AES");
    }
    
    // ================ STATIC INSTANCE MANAGEMENT ================
    
    private static LlmConfigStorage instance;
    
    /**
     * Initialize storage for a server
     */
    public static void init(MinecraftServer server) {
        instance = new LlmConfigStorage(server);
    }
    
    /**
     * Get the current instance
     */
    public static LlmConfigStorage getInstance() {
        return instance;
    }
    
    /**
     * Clear instance on server stop
     */
    public static void shutdown() {
        if (instance != null) {
            instance.save();
            instance = null;
        }
    }
}
