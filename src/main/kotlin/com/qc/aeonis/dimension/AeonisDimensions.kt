package com.qc.aeonis.dimension

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.dimension.DimensionType

object AeonisDimensions {
    val ANCARD: ResourceKey<Level> = ResourceKey.create(
        Registries.DIMENSION,
        Identifier.fromNamespaceAndPath("aeonis", "ancard")
    )
    val ANCARD_TYPE: ResourceKey<DimensionType> = ResourceKey.create(
        Registries.DIMENSION_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "ancard")
    )

    // --- Biome keys ---
    /** @deprecated Use the specific biome keys instead */
    @Deprecated("Use ASH_BARRENS, BLOODROOT_EXPANSE, or VEILSHADE_HOLLOW")
    val ANCARD_BIOME: ResourceKey<Biome> = ResourceKey.create(
        Registries.BIOME,
        Identifier.fromNamespaceAndPath("aeonis", "ancard")
    )

    val ASH_BARRENS: ResourceKey<Biome> = ResourceKey.create(
        Registries.BIOME,
        Identifier.fromNamespaceAndPath("aeonis", "ancard_ash_barrens")
    )
    val BLOODROOT_EXPANSE: ResourceKey<Biome> = ResourceKey.create(
        Registries.BIOME,
        Identifier.fromNamespaceAndPath("aeonis", "bloodroot_expanse")
    )
    val VEILSHADE_HOLLOW: ResourceKey<Biome> = ResourceKey.create(
        Registries.BIOME,
        Identifier.fromNamespaceAndPath("aeonis", "veilshade_hollow")
    )
}
