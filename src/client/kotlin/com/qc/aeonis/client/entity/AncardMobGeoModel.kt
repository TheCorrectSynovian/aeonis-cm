package com.qc.aeonis.client.entity

import net.minecraft.resources.Identifier
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.base.GeoRenderState

/**
 * Resolves GeckoLib assets using the Ancard mob id.
 *
 * GeckoLib 5 expects short resource IDs without prefix/suffix — it resolves them internally:
 *   Model:     `entity/<id>` → `assets/aeonis/geckolib/models/entity/<id>.geo.json`
 *   Animation: `entity/<id>` → `assets/aeonis/geckolib/animations/entity/<id>.animation.json`
 *   Texture:   Full Minecraft resource path: `textures/entity/<id>.png`
 */
class AncardMobGeoModel<T : GeoAnimatable>(private val id: String) : GeoModel<T>() {
    override fun getModelResource(renderState: GeoRenderState): Identifier =
        Identifier.fromNamespaceAndPath("aeonis", "entity/$id")

    override fun getTextureResource(renderState: GeoRenderState): Identifier =
        Identifier.fromNamespaceAndPath("aeonis", "textures/entity/$id.png")

    override fun getAnimationResource(animatable: T): Identifier =
        Identifier.fromNamespaceAndPath("aeonis", "entity/$id")
}

