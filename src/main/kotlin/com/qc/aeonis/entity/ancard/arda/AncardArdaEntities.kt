package com.qc.aeonis.entity.ancard.arda

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

/**
 * Ported Arda's Sculks mobs that are intended to be used in Ancard.
 *
 * NOTE: This file only registers entity types + attributes. No spawn rules here.
 */
object AncardArdaEntities {

    private fun key(path: String): ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("aeonis", path))

    private val RADIOACTIVE_WARDEN_KEY = key("radioactivewarden")
    private val SCULK_BOSS_1_KEY = key("sculkboss1")
    private val SCULK_CREAKING_KEY = key("sculkcreaking")
    private val SCULK_CREEPER_ANIM_KEY = key("sculkcreeperanimation")
    private val SCULK_ENDERMAN_KEY = key("sculkenderman")
    private val SCULK_GOLEM_BOSS_KEY = key("sculkgolembossreloaded")
    private val SCULK_SKELETON_KEY = key("sculkskeleton")
    private val SCULK_SLIME_KEY = key("sculkslime")
    private val SHADOW_HUNTER_KEY = key("shadowhunter")

    val RADIOACTIVE_WARDEN: EntityType<RadioactiveWardenEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        RADIOACTIVE_WARDEN_KEY,
        EntityType.Builder.of(::RadioactiveWardenEntity, MobCategory.MONSTER)
            .sized(0.95f, 2.9f)
            .clientTrackingRange(64)
            .build(RADIOACTIVE_WARDEN_KEY)
    )

    val SCULK_BOSS_1: EntityType<SculkBoss1Entity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SCULK_BOSS_1_KEY,
        EntityType.Builder.of(::SculkBoss1Entity, MobCategory.MONSTER)
            .sized(1.6f, 3.3f)
            .clientTrackingRange(96)
            .build(SCULK_BOSS_1_KEY)
    )

    val SCULK_CREAKING: EntityType<SculkCreakingEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SCULK_CREAKING_KEY,
        EntityType.Builder.of(::SculkCreakingEntity, MobCategory.MONSTER)
            .sized(0.7f, 2.0f)
            .clientTrackingRange(48)
            .build(SCULK_CREAKING_KEY)
    )

    val SCULK_CREEPER_ANIMATION: EntityType<SculkCreeperAnimationEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SCULK_CREEPER_ANIM_KEY,
        EntityType.Builder.of(::SculkCreeperAnimationEntity, MobCategory.MONSTER)
            .sized(0.8f, 1.8f)
            .clientTrackingRange(48)
            .build(SCULK_CREEPER_ANIM_KEY)
    )

    val SCULK_ENDERMAN: EntityType<SculkEndermanEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SCULK_ENDERMAN_KEY,
        EntityType.Builder.of(::SculkEndermanEntity, MobCategory.MONSTER)
            .sized(0.8f, 2.9f)
            .clientTrackingRange(64)
            .build(SCULK_ENDERMAN_KEY)
    )

    val SCULK_GOLEM_BOSS: EntityType<SculkGolemBossReloadedEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SCULK_GOLEM_BOSS_KEY,
        EntityType.Builder.of(::SculkGolemBossReloadedEntity, MobCategory.MONSTER)
            .sized(1.4f, 3.1f)
            .clientTrackingRange(80)
            .build(SCULK_GOLEM_BOSS_KEY)
    )

    val SCULK_SKELETON: EntityType<SculkSkeletonEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SCULK_SKELETON_KEY,
        EntityType.Builder.of(::SculkSkeletonEntity, MobCategory.MONSTER)
            .sized(0.7f, 2.2f)
            .clientTrackingRange(48)
            .build(SCULK_SKELETON_KEY)
    )

    val SCULK_SLIME: EntityType<SculkSlimeEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SCULK_SLIME_KEY,
        EntityType.Builder.of(::SculkSlimeEntity, MobCategory.MONSTER)
            .sized(1.2f, 1.2f)
            .clientTrackingRange(40)
            .build(SCULK_SLIME_KEY)
    )

    val SHADOW_HUNTER: EntityType<ShadowHunterEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SHADOW_HUNTER_KEY,
        EntityType.Builder.of(::ShadowHunterEntity, MobCategory.MONSTER)
            .sized(0.8f, 2.6f)
            .clientTrackingRange(64)
            .build(SHADOW_HUNTER_KEY)
    )

    fun register() {
        FabricDefaultAttributeRegistry.register(RADIOACTIVE_WARDEN, RadioactiveWardenEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SCULK_BOSS_1, SculkBoss1Entity.createAttributes())
        FabricDefaultAttributeRegistry.register(SCULK_CREAKING, SculkCreakingEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SCULK_CREEPER_ANIMATION, SculkCreeperAnimationEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SCULK_ENDERMAN, SculkEndermanEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SCULK_GOLEM_BOSS, SculkGolemBossReloadedEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SCULK_SKELETON, SculkSkeletonEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SCULK_SLIME, SculkSlimeEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SHADOW_HUNTER, ShadowHunterEntity.createAttributes())
    }
}

