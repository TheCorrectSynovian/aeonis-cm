package com.qc.aeonis.mixin.client;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides the camera position (getEyePosition) when controlling a mob.
 * This ensures the camera origin is at the mob's eye level for proper raycasting.
 */
@Mixin(Entity.class)
public class PlayerCameraPosMixin {
    
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void aeonis$getMobEyePosition(float tickDelta, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity)(Object)this;
        Minecraft client = Minecraft.getInstance();
        
        // Only apply to the local player
        if (client.player != null && self == client.player && 
            AeonisClientNetworking.INSTANCE.isControlling() && 
            AeonisClientNetworking.INSTANCE.getControlledMobId() > 0 &&
            client.level != null) {
            
            int mobId = AeonisClientNetworking.INSTANCE.getControlledMobId();
            Entity entity = client.level.getEntity(mobId);
            
            if (entity instanceof LivingEntity living) {
                // Use controlled entity's interpolated eye position for accurate POV raycasting.
                cir.setReturnValue(living.getEyePosition(tickDelta));
            }
        }
    }
}
