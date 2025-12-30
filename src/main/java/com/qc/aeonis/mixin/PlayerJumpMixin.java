package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enhances jump for frog possession with a higher jump and frog animation.
 */
@Mixin(LivingEntity.class)
public class PlayerJumpMixin {
    @Unique
    private boolean aeonis$wasJumpingAsFrog = false;

    @Inject(method = "jumpFromGround()V", at = @At("TAIL"))
    private void onJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
                if (controlledId != null) {
                    var entity = player.level().getEntity(controlledId);
                    if (entity instanceof Mob mob && mob.getType() == EntityType.FROG) {
                        // Enhanced frog jump!
                        Vec3 velocity = player.getDeltaMovement();
                        player.setDeltaMovement(velocity.x, velocity.y + 0.5, velocity.z);
                        player.hurtMarked = true;
                        
                        // Play frog jump sound
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.FROG_LONG_JUMP, player.getSoundSource(), 0.8f, 1.0f);
                        
                        // Trigger frog long jump animation on the mob
                        try {
                            var frog = (net.minecraft.world.entity.animal.frog.Frog) mob;
                            // The animation state can be triggered via entity event
                            mob.level().broadcastEntityEvent(mob, (byte) 6); // Animation event
                        } catch (Exception ignored) {}
                        
                        this.aeonis$wasJumpingAsFrog = true;
                    }
                }
            }
        }
    }
}
