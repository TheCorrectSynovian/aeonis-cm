package com.qc.aeonis

import com.qc.aeonis.command.AeonisCommands
import com.qc.aeonis.config.AeonisFeatures
import com.qc.aeonis.entity.AeonisEntities
import com.qc.aeonis.network.AeonisNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import org.slf4j.LoggerFactory

object AeonisManager : ModInitializer {
    private val logger = LoggerFactory.getLogger("aeonis-manager")

	override fun onInitialize() {
		AeonisEntities.register()
		AeonisCommands.register()
		AeonisNetworking.registerServer()
		
		// Register tick event to enforce spectator mode while transformed
		ServerTickEvents.END_SERVER_TICK.register { server ->
			// Handle pet vex AI redirection
			AeonisCommands.tickPetVexes(server)
            // Handle Director Mode orders
            AeonisCommands.tickActors(server)
            // Handle Thunder Dome waves
            AeonisCommands.tickThunderDomes(server)
			
			for (player in server.playerList.players) {
				if (AeonisNetworking.isPlayerTransformed(player.uuid)) {
					// Force spectator mode while transformed
					if (player.gameMode.gameModeForPlayer != GameType.SPECTATOR) {
						player.setGameMode(GameType.SPECTATOR)
						player.displayClientMessage(
							Component.literal("§c§lYou cannot change gamemode while transformed! Use /untransform first."),
							false
						)
					}
				}
			}
		}
		
		// Register player join event to show welcome message about extra mobs
		ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
			val player = handler.player
			
			// Show message if player hasn't seen it yet and extra mobs are disabled
			if (!AeonisFeatures.hasSeenWelcome(server, player.uuid) && !AeonisFeatures.isExtraMobsEnabled(server)) {
				AeonisFeatures.markWelcomeSeen(server, player.uuid)
				
				// Delay the message slightly so player sees it after login
				server.execute {
					player.sendSystemMessage(Component.literal(""))
					player.sendSystemMessage(Component.literal("§6§l✦ §e§lAeonis Plus §6§l✦"))
					player.sendSystemMessage(Component.literal("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
					player.sendSystemMessage(Component.literal("§eAeonis Special Extra Mobs is currently §c§lOFF§e!"))
					player.sendSystemMessage(Component.literal("§7Give it a try with: §a/aeonis features extra_mobs true"))
					player.sendSystemMessage(Component.literal("§7This adds unique monsters like the §6Stalker§7!"))
					player.sendSystemMessage(Component.literal("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
					player.sendSystemMessage(Component.literal(""))
				}
			}
		}
		
		logger.info("Aeonis Plus core initialized")
	}
}