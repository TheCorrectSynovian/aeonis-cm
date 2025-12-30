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
		com.qc.aeonis.item.AeonisItems.register()
		com.qc.aeonis.data.AeonisLootModifiers.register()
		AeonisPossession.register()
		AeonisCommands.register()
		AeonisNetworking.registerServer()
		
		// Register tick event for transformed players - sync mob position and handle flying
		ServerTickEvents.END_SERVER_TICK.register { server ->
			// Handle pet vex AI redirection
			AeonisCommands.tickPetVexes(server)
            // Handle Director Mode orders
            AeonisCommands.tickActors(server)
            // Handle Thunder Dome waves
            AeonisCommands.tickThunderDomes(server)
			
			for (player in server.playerList.players) {
				if (AeonisNetworking.isPlayerTransformed(player.uuid)) {
					val entityId = AeonisNetworking.getControlledEntityId(player.uuid) ?: continue
					val level = player.level() as? net.minecraft.server.level.ServerLevel ?: continue
					val mob = level.getEntity(entityId)
					
					// AUTO-UNTRANSFORM: If mob is dead or gone, automatically untransform the player
					if (mob == null || !mob.isAlive) {
						AeonisCommands.autoUntransform(player)
						continue
					}
					
					// SYNC MOB TO PLAYER POSITION (player moves via normal minecraft movement, mob follows)
					// This is the QCmod approach - player is the "driver" and mob is the "visual"
					// The camera positioning is handled client-side in CameraMixin
					mob.teleportTo(player.x, player.y, player.z)
					mob.yRot = player.yRot
					mob.xRot = player.xRot
					mob.setYHeadRot(player.yRot)
					if (mob is net.minecraft.world.entity.LivingEntity) {
						mob.yBodyRot = player.yRot
					}
					mob.setDeltaMovement(player.deltaMovement)
					mob.hurtMarked = true
					
					// Sync mob equipment with player's held items
					if (mob is net.minecraft.world.entity.LivingEntity) {
						mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, player.mainHandItem.copy())
						mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, player.offhandItem.copy())
						// Sync armor slots
						mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy())
						mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).copy())
						mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).copy())
						mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).copy())
						
						// Sync health from mob to player (damage redirect happens in mixin)
						val mobMaxHealth = mob.maxHealth.toDouble()
						player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue = mobMaxHealth
						// Keep player health synced with mob
						if (mob.health < player.health) {
							player.health = mob.health
						}
					}
					
					// Handle flying mobs - give player flight ability
					if (AeonisNetworking.canMobFly(mob)) {
						player.abilities.mayfly = true
						player.abilities.flying = true // Force flight for flying mobs
						player.onUpdateAbilities()
					} else {
						// Only disable flight if player wasn't originally in creative
						val originalMode = AeonisCommands.getOriginalGameMode(player.uuid)
						if (originalMode != GameType.CREATIVE && originalMode != GameType.SPECTATOR) {
							player.abilities.mayfly = false
							player.abilities.flying = false
							player.onUpdateAbilities()
						}
					}
					
					// Make player FULLY invisible and disable collisions
					// Player becomes a ghost - only the mob body has collision
					if (!player.isInvisible) {
						player.isInvisible = true
					}
					// Disable player collision by making them spectator-like but still in survival
					player.noPhysics = true
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

			// Additional possession state validation / sync
			server.execute { AeonisPossession.onPlayerJoin(handler.player, server) }
		}

		// Handle disconnect cleanups
		ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
			val player = handler.player
			server.execute { AeonisPossession.onPlayerDisconnect(player, server) }
		}
		
		logger.info("Aeonis Plus core initialized")
	}
}