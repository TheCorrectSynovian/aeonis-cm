package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class ClientPlayerMovementSpeedMixin {
    private static final float PLAYER_BASE_SPEED = 0.1f;

    @Inject(method = "getSpeed()F", at = @At("RETURN"), cancellable = true)
    private void onGetMovementSpeed(CallbackInfoReturnable<Float> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && self == mc.player && AeonisClientNetworking.INSTANCE.isControlling() && AeonisClientNetworking.INSTANCE.getControlledMobId() > 0 && mc.level != null) {
            Entity e = mc.level.getEntity(AeonisClientNetworking.INSTANCE.getControlledMobId());
            if (e instanceof LivingEntity) {
                float mobSpeed = (float)((LivingEntity)e).getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getValue();
                float speedMultiplier = mobSpeed / PLAYER_BASE_SPEED;
                float newSpeed = cir.getReturnValueF() * speedMultiplier;
                if (e.getBbHeight() <= 1.0f) newSpeed *= 3.3f;
                // If mob is a frog (jumping), reduce horizontal speed slightly
                if (e.getType() == net.minecraft.world.entity.EntityType.FROG) newSpeed *= 0.6f;
                cir.setReturnValue(newSpeed);
            }
        }
    }
}
