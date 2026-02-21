package com.qc.aeonis.client.ancard;

import com.qc.aeonis.dimension.AeonisDimensions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.jspecify.annotations.Nullable;

/**
 * Custom fog environment for the Ancard dimension.
 * Creates thick, oppressive fog to limit visibility and enhance atmosphere.
 * Inserted into FogRenderer's environment list before AtmosphericFogEnvironment.
 */
@Environment(EnvType.CLIENT)
public class AncardFogEnvironment extends FogEnvironment {

    private static final float ANCARD_FOG_START = 0.0f;
    private static final float ANCARD_FOG_END = 64.0f;
    private static final float ANCARD_SKY_END = 32.0f;
    private static final float ANCARD_CLOUD_END = 48.0f;

    @Override
    public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
        // Only apply when in atmospheric fog (normal surface view) AND in the Ancard dimension
        return fogType == FogType.ATMOSPHERIC
            && entity.level().dimension() == AeonisDimensions.INSTANCE.getANCARD();
    }

    @Override
    public void setupFog(FogData fogData, Camera camera, ClientLevel clientLevel, float renderDistance, DeltaTracker deltaTracker) {
        // Set dense, oppressive fog similar to Nether's basalt deltas
        fogData.environmentalStart = ANCARD_FOG_START;
        fogData.environmentalEnd = Math.min(renderDistance, ANCARD_FOG_END);
        fogData.skyEnd = ANCARD_SKY_END;
        fogData.cloudEnd = ANCARD_CLOUD_END;
    }

    @Override
    public boolean providesColor() {
        // Let the biome JSON fog_color settings handle colors
        return false;
    }
}

