package com.qc.aeonis.entity.ancard

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.item.Items

/**
 * Registration for all Ancard dimension entities.
 */
object AncardEntities {

    // --- Entity Type Keys ---
    private val ASH_STALKER_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "ash_stalker")
    )

    private val BLOODROOT_FIEND_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "bloodroot_fiend")
    )

    private val VEILSHADE_WATCHER_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "veilshade_watcher")
    )

    private val ANCARD_SOVEREIGN_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "ancard_sovereign")
    )

    private val SHADE_LURKER_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "shade_lurker")
    )

    private val OBELISK_SENTINEL_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "obelisk_sentinel")
    )

    private val CRYPT_MITE_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "crypt_mite")
    )

    private val BONEWEAVER_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "boneweaver")
    )

    private val ECHO_WISP_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "echo_wisp")
    )

    private val RUIN_HOUND_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "ruin_hound")
    )

    private val VEIL_MIMIC_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "veil_mimic")
    )

    private val SPOREBACK_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "sporeback")
    )

    private val RIFT_SCREECHER_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "rift_screecher")
    )

    private val ANCIENT_COLOSSUS_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "ancient_colossus")
    )

    private val OBELISK_SHARD_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("aeonis", "obelisk_shard")
    )

    // --- Entity Types ---
    val ASH_STALKER: EntityType<AshStalkerEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ASH_STALKER_KEY,
        EntityType.Builder.of(::AshStalkerEntity, MobCategory.MONSTER)
            .sized(0.8f, 2.2f)
            .clientTrackingRange(48)
            .fireImmune()
            .build(ASH_STALKER_KEY)
    )

    val BLOODROOT_FIEND: EntityType<BloodrootFiendEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        BLOODROOT_FIEND_KEY,
        EntityType.Builder.of(::BloodrootFiendEntity, MobCategory.MONSTER)
            .sized(0.7f, 2.0f)
            .clientTrackingRange(48)
            .build(BLOODROOT_FIEND_KEY)
    )

    val VEILSHADE_WATCHER: EntityType<VeilshadeWatcherEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        VEILSHADE_WATCHER_KEY,
        EntityType.Builder.of(::VeilshadeWatcherEntity, MobCategory.MONSTER)
            .sized(0.6f, 2.5f)
            .clientTrackingRange(64)
            .build(VEILSHADE_WATCHER_KEY)
    )

    val ANCARD_SOVEREIGN: EntityType<AncardSovereignEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ANCARD_SOVEREIGN_KEY,
        EntityType.Builder.of(::AncardSovereignEntity, MobCategory.MONSTER)
            .sized(1.2f, 3.0f)
            .clientTrackingRange(100)
            .fireImmune()
            .build(ANCARD_SOVEREIGN_KEY)
    )

    val SHADE_LURKER: EntityType<ShadeLurkerEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SHADE_LURKER_KEY,
        EntityType.Builder.of(::ShadeLurkerEntity, MobCategory.MONSTER)
            .sized(1.3f, 1.0f)
            .clientTrackingRange(64)
            .build(SHADE_LURKER_KEY)
    )

    val OBELISK_SENTINEL: EntityType<ObeliskSentinelEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        OBELISK_SENTINEL_KEY,
        EntityType.Builder.of(::ObeliskSentinelEntity, MobCategory.MONSTER)
            .sized(1.2f, 1.8f)
            .clientTrackingRange(96)
            .build(OBELISK_SENTINEL_KEY)
    )

    val CRYPT_MITE: EntityType<CryptMiteEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        CRYPT_MITE_KEY,
        EntityType.Builder.of(::CryptMiteEntity, MobCategory.MONSTER)
            .sized(0.55f, 0.35f)
            .clientTrackingRange(48)
            .build(CRYPT_MITE_KEY)
    )

    val BONEWEAVER: EntityType<BoneweaverEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        BONEWEAVER_KEY,
        EntityType.Builder.of(::BoneweaverEntity, MobCategory.MONSTER)
            .sized(1.2f, 2.0f)
            .clientTrackingRange(64)
            .build(BONEWEAVER_KEY)
    )

    val ECHO_WISP: EntityType<EchoWispEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ECHO_WISP_KEY,
        EntityType.Builder.of(::EchoWispEntity, MobCategory.AMBIENT)
            .sized(0.45f, 0.45f)
            .clientTrackingRange(64)
            .build(ECHO_WISP_KEY)
    )

    val RUIN_HOUND: EntityType<RuinHoundEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        RUIN_HOUND_KEY,
        EntityType.Builder.of(::RuinHoundEntity, MobCategory.MONSTER)
            .sized(1.0f, 1.1f)
            .clientTrackingRange(64)
            .build(RUIN_HOUND_KEY)
    )

    val VEIL_MIMIC: EntityType<VeilMimicEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        VEIL_MIMIC_KEY,
        EntityType.Builder.of(::VeilMimicEntity, MobCategory.MONSTER)
            .sized(1.0f, 1.4f)
            .clientTrackingRange(64)
            .build(VEIL_MIMIC_KEY)
    )

    val SPOREBACK: EntityType<SporebackEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        SPOREBACK_KEY,
        EntityType.Builder.of(::SporebackEntity, MobCategory.CREATURE)
            .sized(2.1f, 1.9f)
            .clientTrackingRange(80)
            .build(SPOREBACK_KEY)
    )

    val RIFT_SCREECHER: EntityType<RiftScreecherEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        RIFT_SCREECHER_KEY,
        EntityType.Builder.of(::RiftScreecherEntity, MobCategory.MONSTER)
            .sized(1.4f, 1.1f)
            .clientTrackingRange(96)
            .build(RIFT_SCREECHER_KEY)
    )

    val ANCIENT_COLOSSUS: EntityType<AncientColossusEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ANCIENT_COLOSSUS_KEY,
        EntityType.Builder.of(::AncientColossusEntity, MobCategory.MONSTER)
            .sized(1.4f, 3.8f)
            .clientTrackingRange(128)
            .fireImmune()
            .build(ANCIENT_COLOSSUS_KEY)
    )

    val OBELISK_SHARD: EntityType<ObeliskShardProjectileEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        OBELISK_SHARD_KEY,
        EntityType.Builder.of(::ObeliskShardProjectileEntity, MobCategory.MISC)
            .sized(0.25f, 0.25f)
            .clientTrackingRange(96)
            .updateInterval(1)
            .build(OBELISK_SHARD_KEY)
    )

    fun register() {
        // Register attributes
        FabricDefaultAttributeRegistry.register(ASH_STALKER, AshStalkerEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(BLOODROOT_FIEND, BloodrootFiendEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(VEILSHADE_WATCHER, VeilshadeWatcherEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(ANCARD_SOVEREIGN, AncardSovereignEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SHADE_LURKER, ShadeLurkerEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(OBELISK_SENTINEL, ObeliskSentinelEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(CRYPT_MITE, CryptMiteEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(BONEWEAVER, BoneweaverEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(ECHO_WISP, EchoWispEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(RUIN_HOUND, RuinHoundEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(VEIL_MIMIC, VeilMimicEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(SPOREBACK, SporebackEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(RIFT_SCREECHER, RiftScreecherEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(ANCIENT_COLOSSUS, AncientColossusEntity.createAttributes())
    }
}
