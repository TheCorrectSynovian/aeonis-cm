package com.qc.aeonis

import com.qc.aeonis.entity.CopperStalkerEntity
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.resources.Identifier

/**
 * Renders Copper Stalker with a standard humanoid/player model.
 */
class CopperStalkerEntityRenderer(ctx: EntityRendererProvider.Context) :
    HumanoidMobRenderer<CopperStalkerEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
        ctx,
        HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)),
        0.5f
    ) {

    companion object {
        private val COPPER_STALKER_TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/entity/copper_stalker.png")
    }

    override fun createRenderState(): HumanoidRenderState = HumanoidRenderState()

    override fun getTextureLocation(state: HumanoidRenderState): Identifier = COPPER_STALKER_TEXTURE
}
