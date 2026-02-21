package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales player movement speed based on the controlled mob's speed attribute.
 * Small mobs get a 3.3x speed boost, frogs get 0.6x modifier.
 */
@Mixin(LivingEntity.class)
public class PlayerMovementSpeedMixin {
    private static final float PLAYER_BASE_SPEED = 0.1f;

    @Inject(method = "getSpeed()F", at = @At("RETURN"), cancellable = true)
    private void onGetMovementSpeed(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
                if (controlledId != null) {
                    var entity = player.level().getEntity(controlledId);
                    if (entity instanceof Mob mob) {
                        float mobSpeed = (float) mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                        float speedMultiplier = mobSpeed / PLAYER_BASE_SPEED;
                        speedMultiplier = Math.max(0.75f, Math.min(speedMultiplier, 1.55f));

                        // Per-mob movement profile tuning for more natural control feel
                        if (mob.getType() == EntityType.ZOMBIE || mob.getType() == EntityType.HUSK || mob.getType() == EntityType.DROWNED) {
                            speedMultiplier *= 0.92f;
                        } else if (mob.getType() == EntityType.CREEPER) {
                            speedMultiplier *= 0.90f;
                        } else if (mob.getType() == EntityType.SPIDER || mob.getType() == EntityType.CAVE_SPIDER) {
                            speedMultiplier *= 1.10f;
                        } else if (mob.getType() == EntityType.IRON_GOLEM || mob.getType() == EntityType.RAVAGER || mob.getType() == EntityType.WARDEN) {
                            speedMultiplier *= 0.82f;
                        }

                        float newSpeed = cir.getReturnValue() * speedMultiplier;
                        
                        // Small mobs get only a light boost (old value was too aggressive).
                        if (mob.getBbHeight() <= 1.0f) {
                            newSpeed *= 1.08f;
                        }
                        
                        // Frogs are jumpy and should feel slightly slower horizontally.
                        if (mob.getType() == EntityType.FROG) {
                            newSpeed *= 0.88f;
                        }

                        cir.setReturnValue(Math.max(0.03f, Math.min(newSpeed, 0.23f)));
                    }
                }
            }
        }
    }
}
