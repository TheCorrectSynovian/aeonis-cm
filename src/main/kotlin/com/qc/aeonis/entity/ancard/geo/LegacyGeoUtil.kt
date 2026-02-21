package com.qc.aeonis.entity.ancard.geo

import software.bernie.geckolib.animation.RawAnimation

/**
 * Helper for legacy/ported GeckoLib animations that don't follow the
 * "animation.<id>.<clip>" naming convention.
 */
object LegacyGeoUtil {
    fun loop(name: String): RawAnimation = RawAnimation.begin().thenLoop(name)
    fun once(name: String): RawAnimation = RawAnimation.begin().thenPlay(name)
}

