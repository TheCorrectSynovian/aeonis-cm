package com.qc.aeonis.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Offsets the world seed by +2 when generating terrain for the Ancard dimension,
 * so it looks like a different overworld rather than a mirror of the player's overworld.
 */
@Mixin(ChunkMap.class)
public abstract class AncardSeedMixin {

    private static final ResourceKey<Level> ANCARD_KEY = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("aeonis", "ancard")
    );

    /**
     * Redirect ServerLevel.getSeed() in the ChunkMap constructor.
     * When the ServerLevel is the Ancard dimension, offset the seed by +2.
     */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"
            )
    )
    private long aeonis$modifyAncardSeed(ServerLevel serverLevel) {
        long seed = serverLevel.getSeed();
        if (serverLevel.dimension().equals(ANCARD_KEY)) {
            return seed + 2L;
        }
        return seed;
    }
}
