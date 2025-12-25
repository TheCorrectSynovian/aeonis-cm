package com.qc.aeonis

import com.qc.aeonis.entity.AeonisEntities
import com.qc.aeonis.entity.CopperStalkerEntity
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.resources.ResourceLocation

object AeonisEntityRenderers {
    private val TEXTURE = ResourceLocation.fromNamespaceAndPath("aeonis", "textures/entity/copper_stalker.png")

    fun register() {
        EntityRendererRegistry.register(AeonisEntities.COPPER_STALKER) { ctx ->
            CopperStalkerRenderer(ctx)
        }
    }

    private class CopperStalkerRenderer(ctx: EntityRendererProvider.Context) :
        HumanoidMobRenderer<CopperStalkerEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
            ctx,
            HumanoidModel(ctx.bakeLayer(ModelLayers.ZOMBIE)),
            0.6f
        ) {
        
        override fun createRenderState(): HumanoidRenderState = HumanoidRenderState()

        override fun getTextureLocation(state: HumanoidRenderState): ResourceLocation = TEXTURE
    }
}
