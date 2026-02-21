package com.qc.aeonis

import com.qc.aeonis.entity.CompanionBotEntity
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.resources.Identifier

class CompanionBotEntityRenderer(ctx: EntityRendererProvider.Context) :
    HumanoidMobRenderer<CompanionBotEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
        ctx,
        HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)),
        0.5f
    ) {

    companion object {
        private val COMPANION_TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/entity/hunter.png")
    }

    override fun createRenderState(): HumanoidRenderState = HumanoidRenderState()

    override fun getTextureLocation(state: HumanoidRenderState): Identifier = COMPANION_TEXTURE
}
