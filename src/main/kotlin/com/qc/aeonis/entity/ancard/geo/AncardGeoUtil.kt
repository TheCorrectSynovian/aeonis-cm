package com.qc.aeonis.entity.ancard.geo

import software.bernie.geckolib.animation.RawAnimation

object AncardGeoUtil {
    fun loop(animId: String, name: String): RawAnimation =
        RawAnimation.begin().thenLoop("animation.$animId.$name")

    fun once(animId: String, name: String): RawAnimation =
        RawAnimation.begin().thenPlay("animation.$animId.$name")
}
