package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import com.qc.aeonis.AeonisPossession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class ServerPlayerNoClipMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            // Enable noPhysics when in possession mode OR when actively controlling a mob
            if (AeonisPossession.INSTANCE.isPlayerInPossessionMode(serverPlayer.getUUID()) || 
                AeonisNetworking.INSTANCE.isPlayerTransformed(serverPlayer.getUUID())) {
                player.noPhysics = true;
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            // Keep noPhysics enabled when in possession mode or controlling a mob
            if (AeonisPossession.INSTANCE.isPlayerInPossessionMode(serverPlayer.getUUID()) || 
                AeonisNetworking.INSTANCE.isPlayerTransformed(serverPlayer.getUUID())) {
                player.noPhysics = true;
                player.setDeltaMovement(new Vec3(player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z));
                player.fallDistance = 0.0F;
            } else if (!AeonisPossession.INSTANCE.isPlayerInPossessionMode(serverPlayer.getUUID()) && 
                       !AeonisNetworking.INSTANCE.isPlayerTransformed(serverPlayer.getUUID()) && 
                       player.noPhysics && !player.isSpectator()) {
                player.noPhysics = false;
            }
        }
    }
}
