package com.qc.aeonis.worldgen

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.placement.PlacedFeature

object AeonisOverworldGeneration {
    private val TUNGSTEN_ORE_DEEP_PLACED_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis_manager", "tungsten_ore_deep")
    )

    private val TUNGSTEN_ORE_STONY_PEAKS_PLACED_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis_manager", "tungsten_ore_stony_peaks")
    )

    private val ANDESITE_STONE_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "andesite_stone_cluster")
    )

    private val LEAF_STAND_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "leaf_stand_cluster")
    )

    private val LIMESTONE_CRYSTAL_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "limestone_crystal_cluster")
    )

    private val SANDSTONE_POLISHED_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "sandstone_polished_cluster")
    )

    private val TRAVERTINE_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "travertine_cluster")
    )

    private val TRUMPED_CONCREATE_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "trumped_concreate_cluster")
    )

    private val TRUMPED_WALL_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "trumped_wall_cluster")
    )

    private val VOLCANIC_STONE_CLUSTER_KEY = ResourceKey.create(
        Registries.PLACED_FEATURE,
        Identifier.fromNamespaceAndPath("aeonis", "volcanic_stone_cluster")
    )

    fun register() {
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            TUNGSTEN_ORE_DEEP_PLACED_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.STONY_PEAKS),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            TUNGSTEN_ORE_STONY_PEAKS_PLACED_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.STONY_PEAKS, Biomes.WINDSWEPT_HILLS, Biomes.JAGGED_PEAKS),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            ANDESITE_STONE_CLUSTER_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.FLOWER_FOREST),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            LEAF_STAND_CLUSTER_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            LIMESTONE_CRYSTAL_CLUSTER_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            SANDSTONE_POLISHED_CLUSTER_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.DESERT, Biomes.SAVANNA, Biomes.WOODED_BADLANDS),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            TRAVERTINE_CLUSTER_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.MEADOW, Biomes.SUNFLOWER_PLAINS),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            TRUMPED_CONCREATE_CLUSTER_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.MEADOW, Biomes.WINDSWEPT_SAVANNA),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            TRUMPED_WALL_CLUSTER_KEY
        )

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(Biomes.BADLANDS, Biomes.JAGGED_PEAKS, Biomes.WINDSWEPT_GRAVELLY_HILLS),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            VOLCANIC_STONE_CLUSTER_KEY
        )
    }
}
