package com.qc.aeonis

import com.qc.aeonis.command.AeonisCommands
import com.qc.aeonis.config.AeonisFeatures
import com.qc.aeonis.entity.AeonisEntities
import com.qc.aeonis.entity.HerobrineEntity
import com.qc.aeonis.entity.HerobrineSpawner
import com.qc.aeonis.llm.AeonisAssistant
import com.qc.aeonis.llm.LlmCommands
import com.qc.aeonis.llm.config.LlmConfigStorage
import com.qc.aeonis.llm.network.LlmNetworking
import com.qc.aeonis.llm.safety.SafetyLimiter
import com.qc.aeonis.companion.CompanionBotManager
import com.qc.aeonis.companion.CompanionCommands
import com.qc.aeonis.minigame.prophunt.PropHuntCommands
import com.qc.aeonis.minigame.prophunt.PropHuntManager
import com.qc.aeonis.network.AeonisNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.GameType
import org.slf4j.LoggerFactory

object AeonisManager : ModInitializer {
    private val logger = LoggerFactory.getLogger("aeonis-manager")
    private val sulfurJumpCooldownByPlayer = mutableMapOf<java.util.UUID, Int>()
    private enum class SulfurArchetype {
        REGULAR,
        BOUNCY,
        SLOW_FLAT,
        FAST_FLAT,
        LIGHT,
        FAST_SLIDING,
        SLOW_SLIDING,
        STICKY,
        HIGH_RESISTANCE
    }

	override fun onInitialize() {
		com.qc.aeonis.block.AeonisBlocks.register()
		com.qc.aeonis.block.entity.AeonisBlockEntities.register()
		// Ancard custom block registry is disabled per current content scope.
		// com.qc.aeonis.block.AncardBlocks.register()
		AeonisEntities.register()
		// Ancard mob systems are intentionally disabled in 26.1 migration.
		// Ancard source remains in-tree but is excluded from compilation.
		// AncardEntities.register()
		// AncardArdaEntities.register()
		com.qc.aeonis.item.AeonisItems.register()
		// Disabled for 26.1 stabilization: some referenced placed-feature keys are not shipped.
		// Re-enable after worldgen datapack/resources are fully aligned with registered content.
		// com.qc.aeonis.worldgen.AeonisOverworldGeneration.register()
		com.qc.aeonis.data.AeonisLootModifiers.register()
		// Ancard mob effects are disabled alongside Ancard mobs.
		// com.qc.aeonis.effect.AncardEffects.register()
		AeonisPossession.register()
		AeonisCommands.register()
		AeonisNetworking.registerServer()
		
		// Register Prop Hunt minigame
		PropHuntManager.register()
		PropHuntCommands.register()
		logger.info("Prop Hunt minigame registered")

		CompanionBotManager.register()
		CompanionCommands.register()
		logger.info("Companion bot system registered")
		
		// Register Manhunt minigame
		com.qc.aeonis.minigame.manhunt.ManhuntManager.register()
		com.qc.aeonis.minigame.manhunt.ManhuntCommands.register()
		logger.info("Manhunt minigame registered")
		
		// Ancard runtime systems are disabled.
		// com.qc.aeonis.dimension.AncardDimensionRules.register()
		// com.qc.aeonis.dimension.AncardEclipseEvent.register()
		// com.qc.aeonis.dimension.AncardSovereignSpawnManager.register()
		
		// Register LLM feature
		LlmCommands.register()
		LlmNetworking.registerServer()
		
		// Initialize LLM config storage when server starts
		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			LlmConfigStorage.init(server)
			val config = LlmConfigStorage.getInstance().config
			SafetyLimiter.init(config.maxBlocksPerMinute, config.maxEditRadius, config.griefProtectionEnabled)
			
			// Initialize script manager
			com.qc.aeonis.llm.script.AeonisScriptManager.initialize(server)
			
			logger.info("LLM config storage and script manager initialized")
		}
		
		// Shutdown LLM on server stop
		ServerLifecycleEvents.SERVER_STOPPING.register { server ->
			AeonisAssistant.despawnAll()
			LlmConfigStorage.shutdown()
			logger.info("LLM shutdown complete")
		}
		
		// Register block break event for Herobrine shrine detection
		PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, blockEntity ->
			if (world is ServerLevel && player is ServerPlayer) {
				// Check if this is a Herobrine shrine block
				HerobrineEntity.onBlockBroken(world, pos, player)
			}
			true // Allow the break to continue
		}
		
		// Register sleep event for Herobrine dream event (creepypasta feature)
		EntitySleepEvents.START_SLEEPING.register { entity, sleepingPos ->
			if (entity is ServerPlayer) {
				HerobrineEntity.triggerDreamEvent(entity)
			}
		}

		// Apply control inputs early in the tick so `LivingEntity.aiStep -> travel(input)` sees them this tick.
		// (In 26.2+, movement is gated behind `isEffectiveAi()`, so we keep NoAI off and suppress AI separately.)
		ServerTickEvents.START_SERVER_TICK.register { server ->
			for (player in server.playerList.players) {
				if (!AeonisNetworking.isPlayerTransformed(player.uuid)) continue
				val entityId = AeonisNetworking.getControlledEntityId(player.uuid) ?: continue
				val level = player.level() as? net.minecraft.server.level.ServerLevel ?: continue
				val mob = level.getEntity(entityId) as? net.minecraft.world.entity.Mob ?: continue
				val input = AeonisNetworking.getLatestControlInput(player.uuid)

				mob.navigation.stop()
				val liveMobSpeed = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).toFloat()
				mob.setSpeed(liveMobSpeed)

					if (input != null) {
						val mag = kotlin.math.sqrt(input.forward * input.forward + input.strafe * input.strafe)
						if (mag > 0.001f) {
							mob.setZza(input.forward / mag)
							mob.setXxa(input.strafe / mag)
						} else {
							mob.setZza(0.0f)
							mob.setXxa(0.0f)
						}

						(mob as? net.minecraft.world.entity.LivingEntity)?.setJumping(input.jump)
						if (AeonisNetworking.canMobFly(mob)) {
							mob.setYya(
								when {
									input.jump -> 1.0f
									input.sneak -> -1.0f
									else -> 0.0f
								}
							)
							// `travel(input)` doesn't use `yya` for vertical movement in air; drive vertical velocity directly.
							val currY = mob.deltaMovement.y
							val targetY = when {
								input.jump -> 0.34
								input.sneak -> -0.26
								else -> 0.0
							}
							val smoothedY = currY + (targetY - currY) * 0.32
							mob.setDeltaMovement(
								mob.deltaMovement.x,
								smoothedY.coerceIn(-0.6, 0.6),
								mob.deltaMovement.z
							)
						} else if (mob.isInWater && mob.canBreatheUnderwater()) {
							// Aquatic mobs: allow vertical swimming via jump/sneak (used by `travelInWater`).
							mob.setYya(
								when {
									input.jump -> 1.0f
									input.sneak -> -1.0f
									else -> 0.0f
								}
							)
						} else {
							mob.setYya(0.0f)
						}
					} else {
						mob.setZza(0.0f)
						mob.setXxa(0.0f)
					mob.setYya(0.0f)
					(mob as? net.minecraft.world.entity.LivingEntity)?.setJumping(false)
				}
			}
		}
		
		// Register tick event for transformed players - sync mob position and handle flying
		ServerTickEvents.END_SERVER_TICK.register { server ->
			// Handle pet vex AI redirection
			AeonisCommands.tickPetVexes(server)
            // Handle temporary prank morph timers
            AeonisCommands.tickPrankMorphs(server)
            // Handle Director Mode orders
            AeonisCommands.tickActors(server)
            // Handle Thunder Dome waves
            AeonisCommands.tickThunderDomes(server)
            // Handle Herobrine spawn cycles
            HerobrineSpawner.tick(server)
			
			for (player in server.playerList.players) {
				if (AeonisNetworking.isPlayerTransformed(player.uuid)) {
					val entityId = AeonisNetworking.getControlledEntityId(player.uuid) ?: continue
					val level = player.level() as? net.minecraft.server.level.ServerLevel ?: continue
					val mob = level.getEntity(entityId)
					val input = AeonisNetworking.getLatestControlInput(player.uuid)
					
					// AUTO-UNTRANSFORM: If mob is dead or gone, automatically untransform the player
					if (mob == null || !mob.isAlive) {
						AeonisCommands.autoUntransform(player)
						continue
					}

					// Cache original movement speed once and mirror the controlled mob's current speed attribute.
					AeonisCommands.cacheOriginalMoveSpeed(player)
					if (mob is net.minecraft.world.entity.LivingEntity) {
						val liveMobSpeed = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
						var targetSpeed = if (liveMobSpeed > 0.0) liveMobSpeed else 0.1
						if (isSulfurCube(mob)) {
							targetSpeed *= when (resolveSulfurArchetype(mob)) {
								SulfurArchetype.FAST_FLAT -> 1.18
								SulfurArchetype.SLOW_FLAT -> 0.76
								SulfurArchetype.FAST_SLIDING -> 1.12
								SulfurArchetype.SLOW_SLIDING -> 0.84
								SulfurArchetype.STICKY -> 0.70
								SulfurArchetype.LIGHT -> 1.06
								else -> 1.0
							}
						}
						player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)?.baseValue = targetSpeed
					}
					
					// Smooth sync player to mob to avoid fight between player motion and mob motion.
					val dx = mob.x - player.x
					val dy = mob.y - player.y
					val dz = mob.z - player.z
					val distSqr = dx * dx + dy * dy + dz * dz
					val posBlend = when {
						distSqr > 36.0 -> 1.0f
						distSqr > 9.0 -> 0.75f
						else -> 0.42f
					}
					player.setPos(
						Mth.lerp(posBlend, player.x.toFloat(), mob.x.toFloat()).toDouble(),
						Mth.lerp(posBlend, player.y.toFloat(), mob.y.toFloat()).toDouble(),
						Mth.lerp(posBlend, player.z.toFloat(), mob.z.toFloat()).toDouble()
					)

					val prevYaw = mob.yRot
					val desiredYaw = player.yRot
					val smoothedYaw = Mth.rotLerp(0.45f, prevYaw, desiredYaw)
					mob.setYRot(smoothedYaw)
					mob.setYHeadRot(smoothedYaw)
					if (mob is net.minecraft.world.entity.LivingEntity) {
						mob.yBodyRot = Mth.rotLerp(0.35f, mob.yBodyRot, smoothedYaw)
					}
					mob.setXRot(player.xRot)
					mob.yRotO = prevYaw
					player.setDeltaMovement(mob.deltaMovement.multiply(0.85, 1.0, 0.85))
					if (mob is net.minecraft.world.entity.LivingEntity) {
						val mobHorizontalSpeed = kotlin.math.sqrt(
							(mob.x - mob.xo) * (mob.x - mob.xo) +
							(mob.z - mob.zo) * (mob.z - mob.zo)
						).toFloat()
						val animSpeed = (mobHorizontalSpeed * 7.2f).coerceIn(0.0f, 2.6f)
						mob.walkAnimation.setSpeed(animSpeed)
						// Sulfur Cube enhancement: use cube-like hop cadence from vanilla cube behavior.
						if (isSulfurCube(mob) && input != null) {
							val archetype = resolveSulfurArchetype(mob)
							val isMoving = kotlin.math.abs(input.forward) > 0.01f || kotlin.math.abs(input.strafe) > 0.01f
							val cooldown = sulfurJumpCooldownByPlayer.getOrDefault(player.uuid, 0)
							if (cooldown > 0) {
								sulfurJumpCooldownByPlayer[player.uuid] = cooldown - 1
							}
							if (player.onGround()) {
								player.setDeltaMovement(
									when (archetype) {
										SulfurArchetype.FAST_SLIDING -> player.deltaMovement.multiply(1.04, 1.0, 1.04)
										SulfurArchetype.SLOW_SLIDING -> player.deltaMovement.multiply(0.92, 1.0, 0.92)
										SulfurArchetype.STICKY -> player.deltaMovement.multiply(0.78, 1.0, 0.78)
										SulfurArchetype.HIGH_RESISTANCE -> player.deltaMovement.multiply(0.88, 1.0, 0.88)
										else -> player.deltaMovement
									}
								)
							}
							if (archetype == SulfurArchetype.LIGHT && !player.onGround()) {
								player.setDeltaMovement(player.deltaMovement.add(0.0, 0.016, 0.0))
							}
							if (isMoving && player.onGround() && mob.onGround() && cooldown <= 0) {
								val jumpImpulse = when (archetype) {
									SulfurArchetype.BOUNCY -> 0.56
									SulfurArchetype.STICKY -> 0.30
									SulfurArchetype.SLOW_FLAT -> 0.28
									SulfurArchetype.FAST_FLAT -> 0.34
									SulfurArchetype.LIGHT -> 0.48
									else -> 0.42
								}
								val boostedY = (player.deltaMovement.y + jumpImpulse).coerceAtMost(0.62)
								player.setDeltaMovement(player.deltaMovement.x, boostedY, player.deltaMovement.z)
								mob.playSound(net.minecraft.sounds.SoundEvents.SLIME_JUMP, 0.5f, 1.0f)
								mob.walkAnimation.setSpeed((animSpeed + 0.35f).coerceAtMost(2.8f))
								sulfurJumpCooldownByPlayer[player.uuid] = when (archetype) {
									SulfurArchetype.BOUNCY -> 7 + level.random.nextInt(8)
									SulfurArchetype.STICKY -> 15 + level.random.nextInt(10)
									SulfurArchetype.SLOW_FLAT -> 13 + level.random.nextInt(8)
									SulfurArchetype.FAST_FLAT -> 9 + level.random.nextInt(7)
									SulfurArchetype.LIGHT -> 8 + level.random.nextInt(8)
									else -> 10 + level.random.nextInt(12)
								}
								player.hurtMarked = true
							}
							if (input.sneak && player.onGround()) {
								val sneakMul = when (archetype) {
									SulfurArchetype.STICKY -> 0.64
									SulfurArchetype.FAST_SLIDING -> 0.90
									else -> 0.80
								}
								player.setDeltaMovement(player.deltaMovement.multiply(sneakMul, 1.0, sneakMul))
							}
						}
					}
					if (distSqr > 0.0001) {
						mob.hurtMarked = true
							player.hurtMarked = true
						}
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
					// Keep normal physics for natural movement while transformed.
					player.noPhysics = false
				}
				else {
					sulfurJumpCooldownByPlayer.remove(player.uuid)
				}
			}
		}
		
		// Register player join event
		ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
			val player = handler.player
			// Ensure stale state from previous sessions cannot leak into this login.
			AeonisNetworking.clearControlState(player.uuid)
			
			// World-level install note: show once per world only.
			if (!AeonisFeatures.hasShownWorldInstallNotice(server)) {
				AeonisFeatures.markWorldInstallNoticeShown(server)
				server.execute {
					player.sendSystemMessage(Component.literal("§eThanks for installing Aeonis! We are still actively porting many lost features to 26.1 (The Tiny Takeover Update)."))
				}
			}

			// Additional possession state validation / sync
			server.execute {
				AeonisPossession.onPlayerJoin(handler.player, server)
				AeonisNetworking.broadcastControllingPlayers(server)
			}
		}

		// Handle disconnect cleanups
		ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
			val player = handler.player
			server.execute {
				AeonisNetworking.clearControlState(player.uuid)
				AeonisNetworking.broadcastControllingPlayers(server)
				AeonisPossession.onPlayerDisconnect(player, server)
			}
		}
		
		logger.info("Aeonis Plus core initialized")
	}

	private fun getYawBlend(entity: net.minecraft.world.entity.Entity): Float {
		val width = entity.bbWidth
		return when {
			entity.type == net.minecraft.world.entity.EntityTypes.SPIDER ||
				entity.type == net.minecraft.world.entity.EntityTypes.CAVE_SPIDER -> 0.52f
			width <= 0.7f -> 0.55f
			width <= 1.2f -> 0.45f
			width <= 2.0f -> 0.36f
			else -> 0.30f
		}
	}

	private fun isSulfurCube(entity: net.minecraft.world.entity.Entity): Boolean {
		return try {
			val path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).path
			path == "sulfur_cube"
		} catch (_: Exception) {
			false
		}
	}

	private fun resolveSulfurArchetype(entity: net.minecraft.world.entity.Entity): SulfurArchetype {
		val living = entity as? net.minecraft.world.entity.LivingEntity ?: return SulfurArchetype.REGULAR
		val bodyStack = living.getItemBySlot(EquipmentSlot.BODY)
		if (bodyStack.isEmpty) return SulfurArchetype.REGULAR
		val itemPath = try {
			net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bodyStack.item).path
		} catch (_: Exception) {
			return SulfurArchetype.REGULAR
		}
		return when (itemPath) {
			"oak_planks" -> SulfurArchetype.BOUNCY
			"iron_block" -> SulfurArchetype.SLOW_FLAT
			"wet_sponge" -> SulfurArchetype.FAST_FLAT
			"white_wool" -> SulfurArchetype.LIGHT
			"packed_ice" -> SulfurArchetype.FAST_SLIDING
			"red_mushroom_block" -> SulfurArchetype.SLOW_SLIDING
			"honeycomb_block" -> SulfurArchetype.STICKY
			"soul_sand" -> SulfurArchetype.HIGH_RESISTANCE
			else -> SulfurArchetype.REGULAR
		}
	}
}


