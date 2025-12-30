package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            if (!AeonisClientNetworking.INSTANCE.isControlling()) return;

            Minecraft mc = Minecraft.getInstance();
            int screenW = mc.getWindow().getGuiScaledWidth();

            float health = AeonisClientNetworking.INSTANCE.getControlledEntityHealth();
            float maxHealth = AeonisClientNetworking.INSTANCE.getControlledEntityMaxHealth();
            String name = AeonisClientNetworking.INSTANCE.getControlledEntityName();

            if (maxHealth <= 0f) return;

            int barWidth = 120;
            int barX = (screenW - barWidth) / 2;
            int barY = 12;

            float frac = Math.max(0f, Math.min(1f, health / maxHealth));
            int filled = (int) Math.round(barWidth * frac);

            // Background
            graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 9, 0x55000000);
            // Empty bar
            graphics.fill(barX, barY, barX + barWidth, barY + 8, 0xFF333333);
            // Filled portion (red)
            graphics.fill(barX, barY, barX + filled, barY + 8, 0xFFFF4444);

            // Name centered above
            int nameW = mc.font.width(name);
            graphics.drawString(mc.font, name, barX + (barWidth - nameW) / 2, barY - 10, 0xFFFFFF);

            // Health text on right side
            String hpText = String.format("%.1f/%.1f", health, maxHealth);
            int hpW = mc.font.width(hpText);
            graphics.drawString(mc.font, hpText, barX + barWidth - hpW, barY + 10, 0xFFFFFF);
        } catch (Throwable ignored) {}
    }
}
