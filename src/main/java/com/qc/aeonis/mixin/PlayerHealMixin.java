package com.qc.aeonis.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import com.qc.aeonis.network.AeonisNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PlayerHealMixin {
    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void onHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) self;
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                ci.cancel();
            }
        }
    }
}
