package com.qc.aeonis.mixin.client;

import com.qc.aeonis.screen.AeonisControlScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Component component) {
        super(component);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void aeonis$addAeonisButton(CallbackInfo ci) {
        Button aeonisButton = Button.builder(Component.translatable("options.aeonis"), button -> {
            this.minecraft.setScreen(new AeonisControlScreen(this));
        }).bounds(this.width / 2 - 100, this.height - 54, 200, 20).build();
        this.addRenderableWidget(aeonisButton);
    }
}
