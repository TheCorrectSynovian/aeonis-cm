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
                        float newSpeed = cir.getReturnValue() * speedMultiplier;
                        
                        // Small mobs (under 1 block tall) get speed boost
                        if (mob.getBbHeight() <= 1.0f) {
                            newSpeed *= 3.3f;
                        }
                        
                        // Frogs get special modifier (slower)
                        if (mob.getType() == EntityType.FROG) {
                            newSpeed *= 0.6f;
                        }
                        
                        cir.setReturnValue(newSpeed);
                    }
                }
            }
        }
    }
}
