package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents mobs from targeting transformed players.
 * - Mobs cannot target invisible player bodies
 * - Mobs of the same type won't attack controlled mobs (e.g., creepers won't attack player-controlled creeper)
 */
@Mixin(Mob.class)
public class MobTargetMixin {
    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void onSetTarget(LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        
        // Case 1: Target is a transformed player - completely cancel targeting
        if (target instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                // Don't target the invisible player at all
                ci.cancel();
                return;
            }
        }
        
        // Case 2: Target is a controlled mob - check if attacker is same mob type
        if (target instanceof Mob targetMob) {
            for (var entry : AeonisNetworking.INSTANCE.getControlledEntitiesMap().entrySet()) {
                if (entry.getValue() == targetMob.getId()) {
                    // This mob is being controlled by a player
                    // If attacker is same type, cancel the attack (friendly fire prevention)
                    if (self.getType() == targetMob.getType()) {
                        ci.cancel();
                        return;
                    }
                    break;
                }
            }
        }
    }
}
