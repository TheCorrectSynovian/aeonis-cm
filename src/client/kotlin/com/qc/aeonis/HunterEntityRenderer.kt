package com.qc.aeonis

import com.qc.aeonis.entity.HunterEntity
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.resources.Identifier

/**
 * Renderer for the Manhunt Hunter using the player (humanoid) model
 * Uses the same texture as Herobrine for that intimidating look
 */
class HunterEntityRenderer(ctx: EntityRendererProvider.Context) :
    HumanoidMobRenderer<HunterEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
        ctx,
        HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)),
        0.5f // Normal shadow for visibility
    ) {

    companion object {
        // Uses the same texture as Herobrine - menacing white eyes
        private val HUNTER_TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/entity/hunter.png")
    }

    override fun createRenderState(): HumanoidRenderState = HumanoidRenderState()

    override fun getTextureLocation(state: HumanoidRenderState): Identifier = HUNTER_TEXTURE
}
