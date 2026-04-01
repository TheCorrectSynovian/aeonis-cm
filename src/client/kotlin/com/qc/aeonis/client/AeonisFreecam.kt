package com.qc.aeonis.client

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

/**
 * Minimal, stable freecam state holder used by client mixins.
 * Defaults keep freecam disabled unless explicitly toggled by future UI/key logic.
 */
object AeonisFreecam {
    private var enabled = false
    private var remoteBreakEnabled = false
    private var anchorPos: Vec3 = Vec3.ZERO

    fun isEnabled(): Boolean = enabled

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (enabled) {
            val player = Minecraft.getInstance().player
            if (player != null) {
                anchorPos = player.position()
            }
        }
    }

    fun isRemoteBreakEnabled(): Boolean = remoteBreakEnabled

    fun setRemoteBreakEnabled(value: Boolean) {
        remoteBreakEnabled = value
    }

    fun getAnchorPos(): Vec3 = anchorPos

    fun setAnchorPos(pos: Vec3) {
        anchorPos = pos
    }

    fun getCameraPos(partialTicks: Float): Vec3 {
        val player = Minecraft.getInstance().player ?: return anchorPos
        return if (enabled) {
            player.getEyePosition(partialTicks)
        } else {
            anchorPos
        }
    }
}
