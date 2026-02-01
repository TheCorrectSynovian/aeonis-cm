package com.qc.aeonis.mixin.client;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the controlled mob in first person view so it doesn't block the camera.
 * This is the key mixin that prevents the mob model from obscuring the player's view.
 */
@Mixin(EntityRenderer.class)
public class MobRenderMixin<T extends Entity> {
    
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void aeonis$hideControlledMobInFirstPerson(T entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        
        // Check if we're in first person view
        boolean isFirstPerson = client.options.getCameraType().isFirstPerson();
        
        // If in first person and controlling this mob, don't render it
        if (isFirstPerson && 
            AeonisClientNetworking.INSTANCE.isControlling() && 
            AeonisClientNetworking.INSTANCE.getControlledMobId() == entity.getId()) {
            cir.setReturnValue(false);
        }
    }
}
