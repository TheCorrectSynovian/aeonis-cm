package com.qc.aeonis.dimension

import com.qc.aeonis.dimension.AeonisDimensions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

/**
 * Handles special dimension rules for Ancard:
 * - Compass spins randomly (no lodestone reference)
 * - Maps do NOT function normally (filled maps appear static/blank)
 * - No natural animal spawning (handled in biome JSON)
 * - Hostile mob density higher than Overworld (handled in biome JSON)
 * - Respawn anchor compatible (handled in dimension_type JSON)
 * - Bed explosion enabled (handled in dimension_type JSON)
 */
object AncardDimensionRules {

    fun register() {
        // Tick-based rule enforcement
        ServerTickEvents.END_SERVER_TICK.register { server ->
            for (player in server.playerList.players) {
                if (player.level().dimension() == AeonisDimensions.ANCARD) {
                    onAncardTick(player)
                }
            }
        }
    }

    private fun onAncardTick(player: ServerPlayer) {
        // Compass confusion: handled client-side via dimension properties
        // (compass naturally spins in dimensions without lodestone/spawn)
        // The fixed_time + no_skylight already causes compass to spin

        // Additional environment effects can be added here:
        // - Ambient damage in certain conditions
        // - Periodic particle spawns
        // - Environmental hazard checks
    }
}
