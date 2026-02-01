package com.qc.aeonis.llm.network;

import com.qc.aeonis.llm.AeonisAssistant;
import com.qc.aeonis.llm.config.LlmConfigStorage;
import com.qc.aeonis.llm.network.payloads.*;
import com.qc.aeonis.llm.provider.LlmProvider;
import com.qc.aeonis.llm.provider.LlmProviderFactory;
import com.qc.aeonis.llm.provider.LlmProviderType;
import com.qc.aeonis.llm.safety.SafetyLimiter;
import com.qc.aeonis.llm.task.BuildTask;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side networking for LLM feature.
 * Handles packet registration and server-side handlers.
 */
public class LlmNetworking {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm-network");
    
    /**
     * Helper to get ServerLevel from player
     */
    private static ServerLevel getServerLevel(ServerPlayer player) {
        return (ServerLevel) player.level();
    }
    
    /**
     * Register all LLM-related packets (call from main mod initializer)
     */
    public static void registerServer() {
        // Register payload types
        PayloadTypeRegistry.playS2C().register(LlmOpenGuiPayload.ID, LlmOpenGuiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LlmStatusUpdatePayload.ID, LlmStatusUpdatePayload.CODEC);
        
        PayloadTypeRegistry.playC2S().register(LlmConfigSubmitPayload.ID, LlmConfigSubmitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LlmTestConnectionPayload.ID, LlmTestConnectionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LlmSpawnBotPayload.ID, LlmSpawnBotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LlmTaskPayload.ID, LlmTaskPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LlmChatMessagePayload.ID, LlmChatMessagePayload.CODEC);
        
        // Register handlers
        registerHandlers();
        
        // Register tick event to tick all AeonisAssistant instances
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                AeonisAssistant assistant = AeonisAssistant.getInstance(level);
                if (assistant != null && assistant.isActive()) {
                    assistant.tick();
                }
            }
        });
        
        LOGGER.info("LLM networking registered");
    }
    
    private static void registerHandlers() {
        // Handle config submission
        ServerPlayNetworking.registerGlobalReceiver(LlmConfigSubmitPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            
            // Permission check
            if (!hasPermission(player)) {
                sendStatus(player, LlmStatusUpdatePayload.error("You don't have permission to configure the LLM."));
                return;
            }
            
            // Process on main thread
            context.server().execute(() -> {
                try {
                    LlmConfigStorage storage = LlmConfigStorage.getInstance();
                    if (storage == null) {
                        sendStatus(player, LlmStatusUpdatePayload.error("Config storage not initialized."));
                        return;
                    }
                    
                    // Update config
                    storage.updateConfig(
                        payload.provider(),
                        payload.model(),
                        payload.hasNewApiKey() ? payload.apiKey() : null,
                        payload.temperature(),
                        payload.maxTokens()
                    );
                    
                    // Update safety settings
                    storage.updateSafetySettings(
                        payload.maxBlocksPerMinute(),
                        payload.maxEditRadius(),
                        payload.griefProtection()
                    );
                    
                    // Update safety limiter instance
                    SafetyLimiter.init(
                        payload.maxBlocksPerMinute(),
                        payload.maxEditRadius(),
                        payload.griefProtection()
                    );
                    
                    sendStatus(player, LlmStatusUpdatePayload.configSaved());
                    LOGGER.info("LLM config updated by {}", player.getName().getString());
                    
                } catch (Exception e) {
                    LOGGER.error("Error saving config: {}", e.getMessage());
                    sendStatus(player, LlmStatusUpdatePayload.error("Failed to save config: " + e.getMessage()));
                }
            });
        });
        
        // Handle connection test
        ServerPlayNetworking.registerGlobalReceiver(LlmTestConnectionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            
            if (!hasPermission(player)) {
                sendStatus(player, LlmStatusUpdatePayload.error("You don't have permission to test the connection."));
                return;
            }
            
            // Test connection asynchronously
            try {
                LlmProviderType providerType = LlmProviderType.fromName(payload.provider());
                LlmProvider provider = LlmProviderFactory.getProvider(providerType);
                LlmProvider.Config config = new LlmProvider.Config(
                    payload.apiKey(),
                    payload.model(),
                    payload.temperature(),
                    100  // Small max tokens for test
                );
                
                LOGGER.info("Testing connection for {} with model {}", 
                    providerType.getDisplayName(), payload.model());
                
                provider.testConnection(config).thenAccept(response -> {
                    // Send result back on main thread
                    context.server().execute(() -> {
                        if (response.success()) {
                            String truncatedResponse = response.content();
                            if (truncatedResponse.length() > 50) {
                                truncatedResponse = truncatedResponse.substring(0, 50) + "...";
                            }
                            sendStatus(player, LlmStatusUpdatePayload.testSuccess(truncatedResponse));
                        } else {
                            sendStatus(player, LlmStatusUpdatePayload.testFailure(response.errorMessage()));
                        }
                    });
                }).exceptionally(ex -> {
                    context.server().execute(() -> {
                        sendStatus(player, LlmStatusUpdatePayload.testFailure(ex.getMessage()));
                    });
                    return null;
                });
                
            } catch (Exception e) {
                LOGGER.error("Test connection error: {}", e.getMessage());
                sendStatus(player, LlmStatusUpdatePayload.testFailure(e.getMessage()));
            }
        });
        
        // Handle spawn/despawn
        ServerPlayNetworking.registerGlobalReceiver(LlmSpawnBotPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            
            if (!hasPermission(player)) {
                sendStatus(player, LlmStatusUpdatePayload.error("You don't have permission to spawn Aeonis."));
                return;
            }
            
            context.server().execute(() -> {
                try {
                    if (payload.spawn()) {
                        // Check if API key is configured
                        LlmConfigStorage storage = LlmConfigStorage.getInstance();
                        if (storage == null || !storage.hasApiKey()) {
                            sendStatus(player, LlmStatusUpdatePayload.spawnFailure("Please configure an API key first."));
                            return;
                        }
                        
                        // Spawn the assistant
                        AeonisAssistant assistant = AeonisAssistant.spawn(player);
                        if (assistant != null) {
                            storage.setBotEnabled(true, player.getUUID());
                            sendStatus(player, LlmStatusUpdatePayload.spawnSuccess());
                        } else {
                            sendStatus(player, LlmStatusUpdatePayload.spawnFailure("Unknown error"));
                        }
                    } else {
                        // Despawn
                        AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
                        if (assistant != null) {
                            assistant.despawn();
                            LlmConfigStorage storage = LlmConfigStorage.getInstance();
                            if (storage != null) {
                                storage.setBotEnabled(false, null);
                            }
                            sendStatus(player, LlmStatusUpdatePayload.info("Aeonis despawned."));
                        } else {
                            sendStatus(player, LlmStatusUpdatePayload.error("Aeonis is not spawned in this dimension."));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Spawn/despawn error: {}", e.getMessage());
                    sendStatus(player, LlmStatusUpdatePayload.error("Error: " + e.getMessage()));
                }
            });
        });
        
        // Handle task commands
        ServerPlayNetworking.registerGlobalReceiver(LlmTaskPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            
            context.server().execute(() -> {
                AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
                if (assistant == null) {
                    player.sendSystemMessage(Component.literal("§cAeonis is not spawned. Use /ai llm spawn first."));
                    return;
                }
                
                switch (payload.taskType()) {
                    case FOLLOW -> {
                        String targetName = payload.targetPlayer();
                        ServerPlayer target = context.server().getPlayerList().getPlayerByName(targetName);
                        if (target != null) {
                            assistant.getAeonisBrain().follow(target);
                        } else {
                            player.sendSystemMessage(Component.literal("§cPlayer '" + targetName + "' not found."));
                        }
                    }
                    case WALK_TO -> {
                        Vec3 pos = new Vec3(payload.x(), payload.y(), payload.z());
                        assistant.getAeonisBrain().navigateTo(pos);
                    }
                    case BUILD -> {
                        BuildTask.Preset preset = BuildTask.Preset.fromName(payload.buildPreset());
                        if (preset != null) {
                            assistant.getAeonisBrain().build(preset);
                        } else {
                            player.sendSystemMessage(Component.literal("§cUnknown build preset: " + payload.buildPreset()));
                            player.sendSystemMessage(Component.literal("§7Available: " + String.join(", ", BuildTask.getPresetNames())));
                        }
                    }
                    case STOP -> {
                        assistant.getAeonisBrain().stopCurrentTask();
                        player.sendSystemMessage(Component.literal("§aAeonis stopped."));
                    }
                }
            });
        });
        
        // Handle chat messages to Aeonis
        ServerPlayNetworking.registerGlobalReceiver(LlmChatMessagePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            
            context.server().execute(() -> {
                AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
                if (assistant != null) {
                    assistant.getAeonisBrain().chat(payload.message(), player);
                }
            });
        });
    }
    
    /**
     * Check if a player has permission to manage LLM config
     * Requires OP level 2+ or singleplayer
     */
    private static boolean hasPermission(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || getServerLevel(player).getServer().isSingleplayer();
    }
    
    /**
     * Send a status update to a player
     */
    public static void sendStatus(ServerPlayer player, LlmStatusUpdatePayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send the open GUI packet to a player
     */
    public static void sendOpenGui(ServerPlayer player) {
        LlmConfigStorage storage = LlmConfigStorage.getInstance();
        if (storage == null) {
            player.sendSystemMessage(Component.literal("§cLLM config not initialized. Please wait for server to finish loading."));
            return;
        }
        
        LlmConfigStorage.LlmConfig config = storage.getConfig();
        
        LlmOpenGuiPayload payload = new LlmOpenGuiPayload(
            config.providerType,
            config.model,
            config.hasApiKey(),
            config.temperature,
            config.maxTokens,
            config.maxBlocksPerMinute,
            config.maxEditRadius,
            config.griefProtectionEnabled
        );
        
        ServerPlayNetworking.send(player, payload);
    }
}
