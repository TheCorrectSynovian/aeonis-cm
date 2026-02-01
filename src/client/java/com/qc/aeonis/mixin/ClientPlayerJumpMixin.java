package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerJumpMixin {
    @Unique
    private boolean wasJumpingAsFrog = false;

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void onJump(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player != null && self == mc.player && AeonisClientNetworking.INSTANCE.isControlling() && AeonisClientNetworking.INSTANCE.getControlledMobId() > 0 && mc.level != null) {
                Entity e = mc.level.getEntity(AeonisClientNetworking.INSTANCE.getControlledMobId());
                if (e instanceof Frog) {
                    Vec3 vel = self.getDeltaMovement();
                    self.setDeltaMovement(vel.x, vel.y + 0.5, vel.z);
                    this.wasJumpingAsFrog = true;
                }
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && self == mc.player && this.wasJumpingAsFrog && self.onGround()) {
            this.wasJumpingAsFrog = false;
        }
    }
}
