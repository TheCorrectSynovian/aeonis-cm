package com.qc.aeonis

import com.qc.aeonis.entity.BodyEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.resources.Identifier

class BodyEntityRenderer(ctx: EntityRendererProvider.Context) :
    HumanoidMobRenderer<BodyEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
        ctx,
        HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)),
        0.6f
    ) {

    private val DEFAULT_BODY_TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/entity/body.png")

    override fun createRenderState(): HumanoidRenderState = HumanoidRenderState()

    // Lightweight update (keeps owner UUID) - skin lookup is not implemented here yet
    fun updateRenderState(entity: BodyEntity, state: com.qc.aeonis.BodyEntityRenderState, tickDelta: Float) {
        state.ownerUuid = entity.getOwnerUuid()
        state.skinTexture = null
        val owner = state.ownerUuid
        if (owner != null) {
            // Try to get from cache immediately
            val cached = com.qc.aeonis.skin.SkinCache.getCached(owner)
            if (cached != null) {
                state.skinTexture = cached
            } else {
                // Request an asynchronous fetch; next frames will pick it up
                com.qc.aeonis.skin.SkinCache.ensureFetchAsync(owner)
            }
        }
    }

    // Return texture based on render state (skin not implemented yet)
    override fun getTextureLocation(state: HumanoidRenderState): Identifier {
        if (state is com.qc.aeonis.BodyEntityRenderState && state.skinTexture != null) {
            return state.skinTexture!!
        }
        return DEFAULT_BODY_TEXTURE
    }
}
