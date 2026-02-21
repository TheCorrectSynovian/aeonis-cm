package com.qc.aeonis.client.dimension

/**
 * Custom rendering hooks for the Ancard dimension.
 *
 * The base atmosphere (fog color, sky color, no skybox, ceiling, no skylight)
 * is now handled by the dimension type JSON at data/aeonis/dimension_type/ancard.json.
 *
 * TODO: Re-implement custom sky geometry (gradient dome, distorted celestial body,
 *       floating embers) using the new 1.21 rendering pipeline when needed.
 */
object AncardDimensionRenderer {

    fun register() {
        // Dimension type JSON handles fog/sky colors natively.
        // Custom GL sky rendering (gradient dome, celestial body, embers)
        // was removed due to Fabric API changes in 1.21+.
        // DimensionRenderingRegistry no longer exists — sky effects are
        // configured via dimension_type JSON attributes:
        //   "minecraft:visual/fog_color": "#120808"
        //   "minecraft:visual/sky_color": "#180510"
        //   "skybox": "none"
    }
}

