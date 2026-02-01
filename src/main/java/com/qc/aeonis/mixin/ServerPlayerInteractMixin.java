package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import com.qc.aeonis.AeonisPossession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1000)
public class ServerPlayerInteractMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V", at = @At("HEAD"), cancellable = true)
    private void onPlayerInteractEntity(ServerboundInteractPacket packet, CallbackInfo ci) {
        // If the player is currently controlling a mob, prevent certain interactions
        if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
            // Allow interactions while possessing
        }
    }
}
