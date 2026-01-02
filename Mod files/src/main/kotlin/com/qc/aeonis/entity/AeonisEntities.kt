package com.qc.aeonis.entity

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.SpawnPlacementTypes
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.level.levelgen.Heightmap

object AeonisEntities {
    private val COPPER_STALKER_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("aeonis", "copper_stalker")
    )

    val COPPER_STALKER: EntityType<CopperStalkerEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        COPPER_STALKER_KEY,
        EntityType.Builder.of(::CopperStalkerEntity, MobCategory.MONSTER)
            .sized(0.6f, 1.95f)
            .clientTrackingRange(48)
            .build(COPPER_STALKER_KEY)
    )
    
    private val HEROBRINE_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("aeonis", "herobrine")
    )
    
    val HEROBRINE: EntityType<HerobrineEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        HEROBRINE_KEY,
        EntityType.Builder.of(::HerobrineEntity, MobCategory.MONSTER)
            .sized(0.6f, 1.95f)
            .clientTrackingRange(100)
            .build(HEROBRINE_KEY)
    )
    
    lateinit var BODY: EntityType<BodyEntity>

    fun register() {
        FabricDefaultAttributeRegistry.register(COPPER_STALKER, CopperStalkerEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(HEROBRINE, HerobrineEntity.createAttributes())

        SpawnPlacements.register(
            COPPER_STALKER,
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            CopperStalkerEntity::canSpawn
        )

        BiomeModifications.addSpawn(
            BiomeSelectors.foundInOverworld(),
            MobCategory.MONSTER,
            COPPER_STALKER,
            60,
            1,
            2
        )
        // Register Body entity used by the possession system
        val BODY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("aeonis", "body"))
        BODY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            BODY_KEY,
            EntityType.Builder.of(::BodyEntity, MobCategory.MISC)
                .sized(0.6f, 1.8f)
                .clientTrackingRange(64)
                .build(BODY_KEY)
        )
        FabricDefaultAttributeRegistry.register(BODY, BodyEntity.createBodyAttributes())
    }
}
