package com.qc.aeonis

import com.qc.aeonis.entity.HerobrineEntity
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.resources.ResourceLocation

/**
 * Renderer for Herobrine using the player (humanoid) model
 * Uses a custom Herobrine skin texture (Steve with white eyes)
 */
class HerobrineEntityRenderer(ctx: EntityRendererProvider.Context) :
    HumanoidMobRenderer<HerobrineEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
        ctx,
        HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)),
        0.0f // No shadow - spooky!
    ) {

    companion object {
        private val HEROBRINE_TEXTURE = ResourceLocation.fromNamespaceAndPath("aeonis", "textures/entity/herobrine.png")
    }

    override fun createRenderState(): HumanoidRenderState = HumanoidRenderState()

    override fun getTextureLocation(state: HumanoidRenderState): ResourceLocation = HEROBRINE_TEXTURE
}
