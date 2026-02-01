package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import com.qc.aeonis.AeonisPossession;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class PlayerItemPickupMixin {
    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void preventItemPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            if (AeonisPossession.INSTANCE.isPlayerInPossessionMode(serverPlayer.getUUID()) && AeonisNetworking.INSTANCE.getControlledEntityId(serverPlayer.getUUID()) == null) {
                ci.cancel();
            }
        }
    }
}
