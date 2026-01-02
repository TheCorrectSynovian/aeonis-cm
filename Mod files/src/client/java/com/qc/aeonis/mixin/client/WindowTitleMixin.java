package com.qc.aeonis.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class WindowTitleMixin {
    
    @Shadow @Final private Window window;
    
    @Inject(method = "updateTitle", at = @At("TAIL"))
    private void modifyWindowTitle(CallbackInfo ci) {
        this.window.setTitle("Minecraft 1.21.10 (Aeonis Plus)");
    }
}
