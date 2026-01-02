package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerBlockCollisionMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickMovement(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!AeonisClientNetworking.INSTANCE.isControlling() && player.noPhysics && !player.isSpectator()) {
            player.noPhysics = false;
        }
    }
}
