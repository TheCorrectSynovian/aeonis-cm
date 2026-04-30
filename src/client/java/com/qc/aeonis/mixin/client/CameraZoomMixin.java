package com.qc.aeonis.mixin.client;

import com.qc.aeonis.client.AeonisKeyBindings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zoom FOV overlay.
 *
 * 26.2+: world FOV is provided by `Camera.getFov()` (GameRenderer no longer exposes `getFov`).
 */
@Mixin(Camera.class)
public class CameraZoomMixin {
    private static float aeonis$zoomProgress = 0.0F;
    private static final float AEONIS_ZOOM_FOV_MULTIPLIER = 0.35F;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void aeonis$applyZoomFov(CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            aeonis$zoomProgress = Math.max(0.0F, aeonis$zoomProgress - 0.25F);
            return;
        }

        float target = AeonisKeyBindings.INSTANCE.getZOOM_KEY().isDown() ? 1.0F : 0.0F;
        aeonis$zoomProgress += (target - aeonis$zoomProgress) * 0.35F;

        if (aeonis$zoomProgress < 0.001F) {
            return;
        }

        float baseFov = cir.getReturnValueF();
        float zoomScale = Mth.lerp(aeonis$zoomProgress, 1.0F, AEONIS_ZOOM_FOV_MULTIPLIER);
        cir.setReturnValue(Math.max(5.0F, baseFov * zoomScale));
    }
}

