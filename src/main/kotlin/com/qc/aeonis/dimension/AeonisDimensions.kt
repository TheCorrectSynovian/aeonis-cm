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
    val ANCARD_BIOME: ResourceKey<Biome> = ResourceKey.create(
        Registries.BIOME,
        Identifier.fromNamespaceAndPath("aeonis", "ancard")
    )
}
