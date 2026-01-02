package com.qc.aeonis.llm;

import com.qc.aeonis.llm.network.payloads.LlmOpenGuiPayload;
import com.qc.aeonis.llm.network.payloads.LlmStatusUpdatePayload;
import com.qc.aeonis.llm.screen.AeonisLlmConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side networking for LLM feature.
 * Registers handlers for server-to-client packets.
 */
@Environment(EnvType.CLIENT)
public class LlmClientNetworking {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm-client");
    
    // Store the last status message for display
    private static LlmStatusUpdatePayload lastStatus;
    private static long lastStatusTime;
    
    /**
     * Register client-side packet handlers (call from client mod initializer)
     */
    public static void register() {
        // Handle open GUI packet
        ClientPlayNetworking.registerGlobalReceiver(LlmOpenGuiPayload.ID, (payload, context) -> {
            // Open the config screen on the client thread
            context.client().execute(() -> {
                Minecraft.getInstance().setScreen(new AeonisLlmConfigScreen(payload));
            });
        });
        
        // Handle status updates
        ClientPlayNetworking.registerGlobalReceiver(LlmStatusUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                lastStatus = payload;
                lastStatusTime = System.currentTimeMillis();
                
                // If a config screen is open, update it
                if (Minecraft.getInstance().screen instanceof AeonisLlmConfigScreen screen) {
                    screen.handleStatusUpdate(payload);
                }
                
                // Log the status
                if (payload.success()) {
                    LOGGER.info("LLM Status: {}", payload.message());
                } else {
                    LOGGER.warn("LLM Error: {}", payload.message());
                }
            });
        });
        
        LOGGER.info("LLM client networking registered");
    }
    
    /**
     * Get the last status update (for display purposes)
     */
    public static LlmStatusUpdatePayload getLastStatus() {
        // Expire after 10 seconds
        if (System.currentTimeMillis() - lastStatusTime > 10000) {
            return null;
        }
        return lastStatus;
    }
    
    /**
     * Clear the last status
     */
    public static void clearStatus() {
        lastStatus = null;
    }
}
