package com.qc.aeonis.dimension

import com.qc.aeonis.dimension.AeonisDimensions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * Rare sky eclipse event in Ancard dimension.
 * Every ~30 real-time minutes, a brief eclipse darkens the dimension further,
 * applying blindness and amplifying mob spawning temporarily.
 */
object AncardEclipseEvent {

    private var eclipseTimer = 0
    private var eclipseActive = false
    private var nextEclipseTick = 36000L // ~30 minutes
    private const val ECLIPSE_DURATION = 600 // 30 seconds
    private var ticksSinceLastEclipse = 0L

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticksSinceLastEclipse++

            val ancardLevel = server.getLevel(AeonisDimensions.ANCARD) ?: return@register

            if (!eclipseActive && ticksSinceLastEclipse >= nextEclipseTick) {
                startEclipse(ancardLevel)
            }

            if (eclipseActive) {
                eclipseTimer--
                if (eclipseTimer <= 0) {
                    endEclipse(ancardLevel)
                }

                // Apply effects to players in Ancard during eclipse
                for (player in server.playerList.players) {
                    if (player.level().dimension() == AeonisDimensions.ANCARD) {
                        applyEclipseEffects(player)
                    }
                }
            }
        }
    }

    private fun startEclipse(level: net.minecraft.server.level.ServerLevel) {
        eclipseActive = true
        eclipseTimer = ECLIPSE_DURATION
        ticksSinceLastEclipse = 0
        nextEclipseTick = 30000L + (Math.random() * 12000).toLong() // 25-35 minutes

        // Notify all players in the dimension
        for (player in level.players()) {
            player.sendSystemMessage(
                Component.literal("§4§l✦ §c§lThe sky darkens... an eclipse descends upon Ancard. §4§l✦")
            )
            level.playSound(
                null, player.blockPosition(),
                SoundEvents.WITHER_SPAWN,
                SoundSource.AMBIENT,
                0.5f, 0.3f
            )
        }
    }

    private fun endEclipse(level: net.minecraft.server.level.ServerLevel) {
        eclipseActive = false

        for (player in level.players()) {
            player.sendSystemMessage(
                Component.literal("§7The eclipse fades... the darkness lifts slightly.")
            )
        }
    }

    private fun applyEclipseEffects(player: ServerPlayer) {
        // Brief periodic blindness pulses during eclipse
        if (eclipseTimer % 100 == 0) {
            player.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false))
        }
        // Constant darkness effect
        if (!player.hasEffect(MobEffects.DARKNESS)) {
            player.addEffect(MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false))
        }
    }

    fun isEclipseActive(): Boolean = eclipseActive
}
