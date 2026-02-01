package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents mobs from being able to "see" transformed players as valid targets.
 * When a player is controlling a mob (e.g., creeper), other mobs won't detect them.
 */
@Mixin(TargetingConditions.class)
public class TargetPredicateMixin {
    @Inject(method = "test(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void onTest(ServerLevel world, LivingEntity baseEntity, LivingEntity targetEntity, CallbackInfoReturnable<Boolean> cir) {
        // If target is a transformed player, they are invisible to ALL mob targeting
        if (targetEntity instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                // Transformed players are completely invisible to mob AI
                cir.setReturnValue(false);
                return;
            }
        }
        
        // If the attacker is a mob and target is a controlled mob, check if they should ignore
        // (Mobs of the same type or friendly mobs shouldn't attack the controlled mob)
        if (baseEntity instanceof Mob attackerMob && targetEntity instanceof Mob targetMob) {
            // Check if targetMob is being controlled by a player
            for (var entry : AeonisNetworking.INSTANCE.getControlledEntitiesMap().entrySet()) {
                if (entry.getValue() == targetMob.getId()) {
                    // This mob is being controlled - check if attacker is same type
                    if (attackerMob.getType() == targetMob.getType()) {
                        // Same mob type shouldn't attack each other
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }
    }
}
