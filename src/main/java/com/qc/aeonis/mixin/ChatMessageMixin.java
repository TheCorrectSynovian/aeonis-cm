package com.qc.aeonis.mixin;

import com.qc.aeonis.llm.AeonisAssistant;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept chat messages and forward them to Aeonis if needed.
 * 
 * When a player mentions "@Aeonis" or "Aeonis," in chat, the message is
 * forwarded to the AI brain for processing.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatMessageMixin {
    
    @Shadow
    public ServerPlayer player;
    
    @Inject(
        method = "broadcastChatMessage",
        at = @At("HEAD")
    )
    private void onChatMessage(PlayerChatMessage message, CallbackInfo ci) {
        String content = message.signedContent();
        
        // Check if Aeonis exists in this dimension
        AeonisAssistant assistant = AeonisAssistant.getInstance((ServerLevel) player.level());
        if (assistant == null) {
            return;
        }
        
        // Check if message mentions Aeonis
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("@aeonis") || 
            lowerContent.contains("aeonis,") ||
            lowerContent.startsWith("aeonis ") ||
            lowerContent.equals("aeonis")) {
            
            // Remove the mention and forward to Aeonis
            String cleanMessage = content
                .replaceAll("(?i)@aeonis", "")
                .replaceAll("(?i)^aeonis[,:]?\\s*", "")
                .trim();
            
            if (!cleanMessage.isEmpty()) {
                // Forward to Aeonis brain
                assistant.getAeonisBrain().chat(cleanMessage, player);
            }
        }
    }
}
