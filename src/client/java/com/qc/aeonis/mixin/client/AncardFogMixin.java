package com.qc.aeonis.mixin.client;

import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin to expose the private FOG_ENVIRONMENTS list so we can add our custom Ancard fog environment.
 */
@Mixin(FogRenderer.class)
public interface AncardFogMixin {
    @Accessor("FOG_ENVIRONMENTS")
    static List<FogEnvironment> aeonis$getFogEnvironments() {
        throw new AssertionError();
    }
}

