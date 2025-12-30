package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerHurtSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        try {
            if (AeonisClientNetworking.INSTANCE.isControlling()) {
                var key = BuiltInRegistries.SOUND_EVENT.getKey(sound);
                if (key != null) {
                    String id = key.toString();
                    if (id.contains("entity.player.hurt") || id.contains("entity.generic.hurt")) {
                        ci.cancel();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
