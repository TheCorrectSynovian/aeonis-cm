package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class ClientPlayerInsideWallMixin {
    @Inject(method = "isInsideWall", at = @At("HEAD"), cancellable = true)
    private void onIsInsideWall(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && self == mc.player && AeonisClientNetworking.INSTANCE.isControlling() && AeonisClientNetworking.INSTANCE.getControlledMobId() > 0 && mc.level != null) {
            Entity e = mc.level.getEntity(AeonisClientNetworking.INSTANCE.getControlledMobId());
            if (e != null && e.getBbHeight() <= 1.0f) {
                cir.setReturnValue(false);
            }
        }
    }
}
