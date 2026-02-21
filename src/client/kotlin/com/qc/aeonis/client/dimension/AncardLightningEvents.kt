package com.qc.aeonis.client.dimension

import com.qc.aeonis.dimension.AeonisDimensions
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Dynamic red lightning events in the Ancard dimension.
 * Creates brief bright red flashes with thunder sound effects.
 */
object AncardLightningEvents {

    private var flashTimer = 0
    private var flashIntensity = 0f
    private var nextFlashTick = 4000L // ~3.3 minutes

    fun register() {
        HudRenderCallback.EVENT.register { graphics, _ ->
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@register

            if (level.dimension() != AeonisDimensions.ANCARD) return@register

            val gameTime = level.gameTime
            val screenWidth = mc.window.guiScaledWidth
            val screenHeight = mc.window.guiScaledHeight

            // Trigger lightning flash
            if (gameTime > 200 && gameTime % nextFlashTick == 0L) {
                flashTimer = 8
                flashIntensity = 0.3f + (Math.random() * 0.4f).toFloat()
                nextFlashTick = 2000 + (Math.random() * 6000).toLong()

                // Play thunder sound
                val player = mc.player
                if (player != null) {
                    level.playLocalSound(
                        player.x, player.y, player.z,
                        SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER,
                        0.6f, 0.5f + (Math.random() * 0.3f).toFloat(),
                        false
                    )
                }
            }

            // Render flash
            if (flashTimer > 0) {
                flashTimer--
                val fade = flashTimer / 8.0f
                val alpha = (flashIntensity * fade * 255).toInt().coerceIn(0, 200)

                // Red-tinted lightning flash
                val color = (alpha shl 24) or 0x401010
                graphics.fill(0, 0, screenWidth, screenHeight, color)

                // Brief white core flash on first frame
                if (flashTimer >= 6) {
                    val whiteAlpha = (alpha * 0.5f).toInt().coerceIn(0, 100)
                    graphics.fill(0, 0, screenWidth, screenHeight, (whiteAlpha shl 24) or 0x604040)
                }
            }
        }
    }
}
