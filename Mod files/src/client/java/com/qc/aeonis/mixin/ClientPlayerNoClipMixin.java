package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerNoClipMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;noPhysics:Z", shift = At.Shift.AFTER))
    private void onTickAfterNoClipClient(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        // Keep noPhysics enabled while controlling a mob
        if (AeonisClientNetworking.INSTANCE.isControlling()) {
            player.noPhysics = true;
        } else if (!AeonisClientNetworking.INSTANCE.isControlling() && player.noPhysics && !player.isSpectator()) {
            player.noPhysics = false;
        }
    }
}
