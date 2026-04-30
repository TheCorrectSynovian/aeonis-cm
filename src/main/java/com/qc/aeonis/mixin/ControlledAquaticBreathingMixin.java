package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While controlling an aquatic mob, the player's air bubbles should not drain.
 *
 * In vanilla, drowning is driven by `LivingEntity.baseTick()` and checks `canBreatheUnderwater()`.
 * When we move the camera to the mob's eye position, the player is still physically in water and
 * would lose air even if the controlled mob can breathe underwater.
 */
@Mixin(LivingEntity.class)
public class ControlledAquaticBreathingMixin {
    @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
    private void aeonis$inheritUnderwaterBreathingWhileControlled(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }
        if (!AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
            return;
        }

        Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
        if (controlledId == null) {
            return;
        }

        Entity controlled = player.level().getEntity(controlledId);
        if (controlled instanceof LivingEntity controlledLiving && controlledLiving.canBreatheUnderwater()) {
            cir.setReturnValue(true);
        }
    }
}

