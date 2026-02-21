package com.qc.aeonis.client.entity

import com.qc.aeonis.entity.ancard.AncardEntities
import com.qc.aeonis.entity.ancard.AshStalkerEntity
import com.qc.aeonis.entity.ancard.BloodrootFiendEntity
import com.qc.aeonis.entity.ancard.VeilshadeWatcherEntity
import com.qc.aeonis.entity.ancard.AncardSovereignEntity
import com.qc.aeonis.entity.ancard.AncientColossusEntity
import com.qc.aeonis.entity.ancard.BoneweaverEntity
import com.qc.aeonis.entity.ancard.CryptMiteEntity
import com.qc.aeonis.entity.ancard.EchoWispEntity
import com.qc.aeonis.entity.ancard.ObeliskSentinelEntity
import com.qc.aeonis.entity.ancard.RiftScreecherEntity
import com.qc.aeonis.entity.ancard.RuinHoundEntity
import com.qc.aeonis.entity.ancard.ShadeLurkerEntity
import com.qc.aeonis.entity.ancard.SporebackEntity
import com.qc.aeonis.entity.ancard.VeilMimicEntity
import com.qc.aeonis.entity.ancard.arda.AncardArdaEntities
import com.qc.aeonis.entity.ancard.arda.RadioactiveWardenEntity
import com.qc.aeonis.entity.ancard.arda.SculkBoss1Entity
import com.qc.aeonis.entity.ancard.arda.SculkCreakingEntity
import com.qc.aeonis.entity.ancard.arda.SculkCreeperAnimationEntity
import com.qc.aeonis.entity.ancard.arda.SculkEndermanEntity
import com.qc.aeonis.entity.ancard.arda.SculkGolemBossReloadedEntity
import com.qc.aeonis.entity.ancard.arda.SculkSkeletonEntity
import com.qc.aeonis.entity.ancard.arda.SculkSlimeEntity
import com.qc.aeonis.entity.ancard.arda.ShadowHunterEntity
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.renderer.entity.ThrownItemRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Client-side entity renderer registration for all Ancard dimension mobs.
 */
object AncardEntityRenderers {

    fun register() {
        // All Ancard mobs now use GeckoLib renderers
        EntityRendererRegistry.register(AncardEntities.ASH_STALKER) { ctx ->
            AncardGeoRenderer<AshStalkerEntity>(ctx, "ash_stalker", 0.7f)
        }

        EntityRendererRegistry.register(AncardEntities.BLOODROOT_FIEND) { ctx ->
            AncardGeoRenderer<BloodrootFiendEntity>(ctx, "bloodroot_fiend", 0.5f)
        }

        EntityRendererRegistry.register(AncardEntities.VEILSHADE_WATCHER) { ctx ->
            AncardGeoRenderer<VeilshadeWatcherEntity>(ctx, "veilshade_watcher", 0.0f)
        }

        EntityRendererRegistry.register(AncardEntities.ANCARD_SOVEREIGN) { ctx ->
            AncardGeoRenderer<AncardSovereignEntity>(ctx, "ancard_sovereign", 1.2f)
        }

        EntityRendererRegistry.register(AncardEntities.SHADE_LURKER) { ctx ->
            AncardGeoRenderer<ShadeLurkerEntity>(ctx, "shade_lurker", 0.7f)
        }
        EntityRendererRegistry.register(AncardEntities.OBELISK_SENTINEL) { ctx ->
            AncardGeoRenderer<ObeliskSentinelEntity>(ctx, "obelisk_sentinel", 0.8f)
        }
        EntityRendererRegistry.register(AncardEntities.CRYPT_MITE) { ctx ->
            AncardGeoRenderer<CryptMiteEntity>(ctx, "crypt_mite", 0.3f)
        }
        EntityRendererRegistry.register(AncardEntities.BONEWEAVER) { ctx ->
            AncardGeoRenderer<BoneweaverEntity>(ctx, "boneweaver", 0.7f)
        }
        EntityRendererRegistry.register(AncardEntities.ECHO_WISP) { ctx ->
            AncardGeoRenderer<EchoWispEntity>(ctx, "echo_wisp", 0.25f)
        }
        EntityRendererRegistry.register(AncardEntities.RUIN_HOUND) { ctx ->
            AncardGeoRenderer<RuinHoundEntity>(ctx, "ruin_hound", 0.6f)
        }
        EntityRendererRegistry.register(AncardEntities.VEIL_MIMIC) { ctx ->
            AncardGeoRenderer<VeilMimicEntity>(ctx, "veil_mimic", 0.7f)
        }
        EntityRendererRegistry.register(AncardEntities.SPOREBACK) { ctx ->
            AncardGeoRenderer<SporebackEntity>(ctx, "sporeback", 0.95f)
        }
        EntityRendererRegistry.register(AncardEntities.RIFT_SCREECHER) { ctx ->
            AncardGeoRenderer<RiftScreecherEntity>(ctx, "rift_screecher", 0.8f)
        }
        EntityRendererRegistry.register(AncardEntities.ANCIENT_COLOSSUS) { ctx ->
            AncardGeoRenderer<AncientColossusEntity>(ctx, "ancient_colossus", 1.0f)
        }

        // Ported Arda's Sculks mobs (Ancard usage)
        EntityRendererRegistry.register(AncardArdaEntities.RADIOACTIVE_WARDEN) { ctx ->
            AncardGeoRenderer<RadioactiveWardenEntity>(ctx, "radioactivewarden", 1.0f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SCULK_BOSS_1) { ctx ->
            AncardGeoRenderer<SculkBoss1Entity>(ctx, "sculkboss1", 1.4f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SCULK_CREAKING) { ctx ->
            AncardGeoRenderer<SculkCreakingEntity>(ctx, "sculkcreaking", 0.6f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SCULK_CREEPER_ANIMATION) { ctx ->
            AncardGeoRenderer<SculkCreeperAnimationEntity>(ctx, "sculkcreeperanimation", 0.6f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SCULK_ENDERMAN) { ctx ->
            AncardGeoRenderer<SculkEndermanEntity>(ctx, "sculkenderman", 0.9f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SCULK_GOLEM_BOSS) { ctx ->
            AncardGeoRenderer<SculkGolemBossReloadedEntity>(ctx, "sculkgolembossreloaded", 1.2f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SCULK_SKELETON) { ctx ->
            AncardGeoRenderer<SculkSkeletonEntity>(ctx, "sculkskeleton", 0.6f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SCULK_SLIME) { ctx ->
            AncardGeoRenderer<SculkSlimeEntity>(ctx, "sculkslime", 0.7f)
        }
        EntityRendererRegistry.register(AncardArdaEntities.SHADOW_HUNTER) { ctx ->
            AncardGeoRenderer<ShadowHunterEntity>(ctx, "shadowhunter", 0.9f)
        }

        // Projectile renderer (item-based).
        EntityRendererRegistry.register(AncardEntities.OBELISK_SHARD) { ctx ->
            ThrownItemRenderer(ctx)
        }
    }

    private class AncardGeoRenderer<T>(
        ctx: EntityRendererProvider.Context,
        id: String,
        shadow: Float,
    ) : GeoEntityRenderer<T, LivingEntityRenderState>(ctx, AncardMobGeoModel(id))
        where T : net.minecraft.world.entity.Entity, T : software.bernie.geckolib.animatable.GeoAnimatable {
        init {
            shadowRadius = shadow
        }
    }
}
