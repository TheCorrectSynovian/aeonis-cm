package com.qc.aeonis

import com.qc.aeonis.entity.AeonisEntities
import com.qc.aeonis.entity.CopperStalkerEntity
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.resources.Identifier

object AeonisEntityRenderers {
    private val TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/entity/copper_stalker.png")
    private val BODY_TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/entity/body.png")

    fun register() {
        EntityRendererRegistry.register(AeonisEntities.COPPER_STALKER) { ctx ->
            CopperStalkerRenderer(ctx)
        }
        // Register the Body entity renderer (uses player skin when available)
        EntityRendererRegistry.register(AeonisEntities.BODY) { ctx ->
            BodyEntityRenderer(ctx)
        }
        // Register Herobrine entity renderer (uses humanoid player model)
        EntityRendererRegistry.register(AeonisEntities.HEROBRINE) { ctx ->
            HerobrineEntityRenderer(ctx)
        }
        // Register Hunter entity renderer for Manhunt minigame
        EntityRendererRegistry.register(AeonisEntities.HUNTER) { ctx ->
            HunterEntityRenderer(ctx)
        }
    }

    private class CopperStalkerRenderer(ctx: EntityRendererProvider.Context) :
        HumanoidMobRenderer<CopperStalkerEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
            ctx,
            HumanoidModel(ctx.bakeLayer(ModelLayers.ZOMBIE)),
            0.6f
        ) {
        
        override fun createRenderState(): HumanoidRenderState = HumanoidRenderState()

        override fun getTextureLocation(state: HumanoidRenderState): Identifier = TEXTURE
    }
}
