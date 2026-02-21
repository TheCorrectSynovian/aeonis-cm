package com.qc.aeonis.client.dimension

import com.qc.aeonis.dimension.AeonisDimensions
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.Identifier

/**
 * Ancard post-processing visual effects.
 * Renders HUD-level overlays for:
 * - Screen vignette (dark borders)
 * - Red ambient tint
 * - Occasional distortion pulse events
 * - Dynamic fog color shifting based on biome
 */
object AncardPostProcessing {

    private var distortionTimer = 0
    private var distortionActive = false
    private var nextDistortionTick = 2400 // ~2 minutes initially

    fun register() {
        HudRenderCallback.EVENT.register { graphics, tickCounter ->
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@register
            val player = mc.player ?: return@register

            // Only apply effects in Ancard dimension
            if (level.dimension() != AeonisDimensions.ANCARD) return@register

            val screenWidth = mc.window.guiScaledWidth
            val screenHeight = mc.window.guiScaledHeight
            val partialTick = tickCounter.getGameTimeDeltaPartialTick(false)

            // 1. Dark vignette overlay
            renderVignette(graphics, screenWidth, screenHeight)

            // 2. Subtle red ambient tint
            renderRedTint(graphics, screenWidth, screenHeight)

            // 3. Occasional distortion pulse
            handleDistortionPulse(graphics, screenWidth, screenHeight, level.gameTime)
        }
    }

    private fun renderVignette(graphics: GuiGraphics, width: Int, height: Int) {
        // Dark borders vignette effect
        val borderSize = 40
        val alpha = 120 // 0-255

        // Top border
        graphics.fill(0, 0, width, borderSize, (alpha shl 24) or 0x050005)
        // Bottom border
        graphics.fill(0, height - borderSize, width, height, (alpha shl 24) or 0x050005)
        // Left border
        graphics.fill(0, 0, borderSize, height, (alpha shl 24) or 0x050005)
        // Right border
        graphics.fill(width - borderSize, 0, width, height, (alpha shl 24) or 0x050005)

        // Softer inner vignette
        val innerBorder = borderSize / 2
        val innerAlpha = 60
        graphics.fill(0, 0, width, innerBorder + borderSize, (innerAlpha shl 24) or 0x050005)
        graphics.fill(0, height - innerBorder - borderSize, width, height, (innerAlpha shl 24) or 0x050005)
    }

    private fun renderRedTint(graphics: GuiGraphics, width: Int, height: Int) {
        // Very subtle red overlay across entire screen
        val tintAlpha = 15 // Very subtle
        graphics.fill(0, 0, width, height, (tintAlpha shl 24) or 0x400808)
    }

    private fun handleDistortionPulse(graphics: GuiGraphics, width: Int, height: Int, gameTime: Long) {
        // Check if it's time for a distortion pulse
        if (!distortionActive && gameTime % nextDistortionTick.toLong() == 0L && gameTime > 0) {
            distortionActive = true
            distortionTimer = 30 // ~1.5 seconds
            nextDistortionTick = 1200 + (Math.random() * 3600).toInt() // 1-4 minutes
        }

        if (distortionActive) {
            distortionTimer--
            if (distortionTimer <= 0) {
                distortionActive = false
                return
            }

            // Pulsing distortion overlay
            val intensity = kotlin.math.sin(distortionTimer * 0.3).toFloat()
            val pulseAlpha = (kotlin.math.abs(intensity) * 40).toInt().coerceIn(0, 60)

            // Chromatic aberration simulation: offset red and blue channels
            val offset = (intensity * 3).toInt()

            // Red channel shift
            graphics.fill(offset, 0, width + offset, height, (pulseAlpha shl 24) or 0x300000)
            // Blue channel shift (opposite direction)
            graphics.fill(-offset, 0, width - offset, height, (pulseAlpha shl 24) or 0x000030)
        }
    }
}

