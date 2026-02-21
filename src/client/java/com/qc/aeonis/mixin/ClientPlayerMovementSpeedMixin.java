package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
                speedMultiplier = Math.max(0.75f, Math.min(speedMultiplier, 1.55f));
                if (e.getType() == EntityType.ZOMBIE || e.getType() == EntityType.HUSK || e.getType() == EntityType.DROWNED) {
                    speedMultiplier *= 0.92f;
                } else if (e.getType() == EntityType.CREEPER) {
                    speedMultiplier *= 0.90f;
                } else if (e.getType() == EntityType.SPIDER || e.getType() == EntityType.CAVE_SPIDER) {
                    speedMultiplier *= 1.10f;
                } else if (e.getType() == EntityType.IRON_GOLEM || e.getType() == EntityType.RAVAGER || e.getType() == EntityType.WARDEN) {
                    speedMultiplier *= 0.82f;
                }

                float newSpeed = cir.getReturnValueF() * speedMultiplier;
                if (e.getBbHeight() <= 1.0f) newSpeed *= 1.08f;
                if (e.getType() == EntityType.FROG) newSpeed *= 0.88f;
                cir.setReturnValue(Math.max(0.03f, Math.min(newSpeed, 0.23f)));
            }
        }
    }
}
