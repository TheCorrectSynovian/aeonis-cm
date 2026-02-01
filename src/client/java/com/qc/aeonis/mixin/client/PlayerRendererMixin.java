package com.qc.aeonis.mixin.client;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides player model when controlling a mob by moving it far off-screen
 * Based on QCmod implementation - targets AvatarRenderer (renamed from PlayerRenderer in 1.21.10)
 */
@Mixin(AvatarRenderer.class)
public class PlayerRendererMixin {
    
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void aeonis$hidePlayer(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer) {
            AbstractClientPlayer player = (AbstractClientPlayer) entity;
            Minecraft client = Minecraft.getInstance();
            
            boolean shouldHide = false;
            if (AeonisClientNetworking.INSTANCE.isControlling() && player == client.player) {
                shouldHide = true;
            }
            
            if (shouldHide) {
                state.x = -10000.0;
            }
        }
    }
}
