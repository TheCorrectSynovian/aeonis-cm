package com.qc.aeonis

import com.qc.aeonis.item.AeonisItems
import com.qc.aeonis.entity.BodyEntity
import com.qc.aeonis.network.AeonisNetworking
import com.qc.aeonis.entity.AeonisEntities
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.network.chat.Component
import java.util.UUID

object AeonisPossession {
    private val POSSESS_ID = ResourceLocation.fromNamespaceAndPath("aeonis", "possess_mob")
    private val RELEASE_ID = ResourceLocation.fromNamespaceAndPath("aeonis", "release_mob")
    private val ABILITY_ID = ResourceLocation.fromNamespaceAndPath("aeonis", "ability")

    // Possession state tracking maps
    val playerBodies = mutableMapOf<UUID, BodyEntity>()
    val activePossessions = mutableMapOf<UUID, Boolean>()
    val bodyPositions = mutableMapOf<UUID, DoubleArray>()
    val mobInventories = mutableMapOf<Int, Array<ItemStack?>>()

    // Backup maps
    private val inventoryBackups = mutableMapOf<UUID, Array<ItemStack?>>()
    private val originalGameModes = mutableMapOf<UUID, GameType>()
    // Player -> previous selected slot (restored on release)
    private val playerPreviousSelectedSlot = mutableMapOf<UUID, Int>()
    // Mob ID -> last selected slot (saved on disconnect)
    private val mobLastSelectedSlot = mutableMapOf<Int, Int>()

    // Helper for BodyEntity and other systems to check original game mode
    private val playersInPossessionMode = mutableSetOf<UUID>()

    fun isOriginalModeSpectator(uuid: UUID): Boolean {
        return originalGameModes[uuid] == GameType.SPECTATOR
    }

    fun enterPossessionMode(uuid: UUID) {
        playersInPossessionMode.add(uuid)
    }

    fun exitPossessionMode(uuid: UUID) {
        playersInPossessionMode.remove(uuid)
    }

    fun isPlayerInPossessionMode(uuid: UUID): Boolean = playersInPossessionMode.contains(uuid)

    fun register() {
        // UseEntity callback for soul possession interactions (using SOUL item)
        UseEntityCallback.EVENT.register(UseEntityCallback { player, world, hand, entity, hitResult ->
            val stack = player.getItemInHand(hand)
            if (stack.item === AeonisItems.SOUL) {
                if (world.isClientSide) return@UseEntityCallback InteractionResult.SUCCESS
                if (entity is LivingEntity && entity !is BodyEntity) {
                    handlePossess(player as ServerPlayer, entity)
                    return@UseEntityCallback InteractionResult.CONSUME
                } else {
                    (player as? ServerPlayer)?.sendSystemMessage(Component.literal("§c[AEONIS] Can only possess mobs!"))
                    return@UseEntityCallback InteractionResult.PASS
                }
            }
            InteractionResult.PASS
        })

        // Keep body position map updated each server tick
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register { server: net.minecraft.server.MinecraftServer ->
            for ((playerId, body) in playerBodies) {
                if (!body.isAlive) continue
                bodyPositions[playerId] = doubleArrayOf(body.x, body.y, body.z, body.yRot.toDouble())
            }
        }
    }

    fun handlePossess(player: ServerPlayer, target: Entity, server: net.minecraft.server.MinecraftServer? = null) {
        if (target !is LivingEntity) return
        val playerId = player.uuid
        if (activePossessions.getOrDefault(playerId, false)) {
            player.sendSystemMessage(Component.literal("§c[AEONIS] Already possessing a mob."))
            return
        }

        // Spawn body to represent player using registered entity type
        val world = player.level()
        val body = BodyEntity(AeonisEntities.BODY, world)
        body.teleportTo(player.x, player.y, player.z)
        body.yRot = player.yRot
        body.xRot = player.xRot
        body.setOwnerUuid(player.uuid)
        world.addFreshEntity(body)

        playerBodies[playerId] = body
        activePossessions[playerId] = true

        // Backup player inventory
        val invSize = player.inventory.containerSize
        val inv = Array<ItemStack?>(invSize) { i -> player.inventory.getItem(i).copy() }
        inventoryBackups[playerId] = inv

        // Save player's currently selected hotbar slot so we can restore it on release
        try {
            playerPreviousSelectedSlot[playerId] = player.inventory.selectedSlot
        } catch (_: Exception) {
            // ignore if not available in current mappings
        }

        // Save the target mob's inventory for future restore (so mobs keep their equipment between possessions)
        val size = player.inventory.containerSize
        val arr = Array<ItemStack?>(size) { null }
        // Map mob equipment into player inventory slots similar to the old QCmod layout
        // slot 0 = mainhand, 40 = offhand, 39 = head, 38 = legs, 37 = chest, 36 = feet (if within bounds)
        if (size > 0) arr[0] = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).copy()
        if (size > 40) arr[40] = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).copy()
        if (size > 39) arr[39] = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy()
        if (size > 38) arr[38] = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).copy()
        if (size > 37) arr[37] = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).copy()
        if (size > 36) arr[36] = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).copy()
        mobInventories[target.id] = arr

        // Load inventory into player (equip player with the mob's items)
        loadInventoryFromMob(player, target)

        // Ensure the player's active hotbar is set to the mob's main hand (slot 0) for immediate control
        try {
            player.inventory.selectedSlot = 0
        } catch (_: Exception) {
            // ignore if mapping doesn't allow directly setting selected slot
        }

        // Set player's health to mob's health and adjust max health attribute
        val mobMaxHealth = target.maxHealth.toDouble()
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue = mobMaxHealth
        player.health = target.health.coerceAtMost(mobMaxHealth.toFloat())

        // Set original gamemode and restore appropriate mode while controlling
        val originalMode = player.gameMode.gameModeForPlayer
        originalGameModes[playerId] = originalMode
        if (originalMode == GameType.SPECTATOR) {
            // If they were originally spectator, keep spectator
            player.setGameMode(GameType.SPECTATOR)
        } else {
            // Allow block interaction and hotbar use while possessing a mob by using SURVIVAL and making the player invisible
            player.setGameMode(GameType.SURVIVAL)
            player.isInvisible = true
        }

        // Set controlled entity in AeonisNetworking (pass selected slot so server knows which slot the player had)
        AeonisNetworking.setControlledEntity(player, target.id, try { player.inventory.selectedSlot } catch (_: Exception) { -1 })
        // Broadcast updated controlling players if server is available
        if (server != null) server.execute { AeonisNetworking.broadcastControllingPlayers(server) }

        player.sendSystemMessage(Component.literal("§a[AEONIS] Possessed ${target.name.string}! Press release key to return."))
    }

    fun handleRelease(player: ServerPlayer, clientBodyX: Double, clientBodyY: Double, clientBodyZ: Double) {
        // Additional cleanup: ensure controlled entity mapping removed when releasing
        val playerId = player.uuid
        AeonisNetworking.removeControlledEntity(player)
        if (!activePossessions.getOrDefault(playerId, false)) return

        val body = playerBodies.remove(playerId)
        activePossessions.remove(playerId)
        bodyPositions.remove(playerId)

        // Save current player inventory back to mob if controlling one
        val controlledId = AeonisNetworking.getControlledEntityId(player.uuid)
        if (controlledId != null) {
            val entity = player.level().getEntity(controlledId)
            if (entity is LivingEntity) {
                saveInventoryToMob(player, entity)
                try { player.isInvisible = false } catch (_: Exception) {}
            }
        }

        // Restore inventory backup
        val backup = inventoryBackups.remove(playerId)
        if (backup != null) {
            for (i in backup.indices) {
                val stack = backup[i]
                if (stack != null) player.inventory.setItem(i, stack)
            }
        }

        // Remove controlled entity association
        AeonisNetworking.removeControlledEntity(player)

        // Restore gamemode and visibility
        player.isInvisible = false
        val original = originalGameModes.remove(playerId)
        if (original != null) player.setGameMode(original)

        // Reset attributes to normal player defaults
        val finalHealth = body?.health ?: player.health
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue = 20.0
        player.health = finalHealth.coerceAtMost(20.0f)

        // Restore previous selected hotbar slot if we saved one
        val prevSelected = playerPreviousSelectedSlot.remove(playerId)
        if (prevSelected != null) {
            try {
                player.inventory.selectedSlot = prevSelected
            } catch (_: Exception) {
                // ignore if mapping doesn't allow direct assignment
            }
        }

        if (body != null && body.isAlive) {
            player.teleportTo(body.x, body.y, body.z)
            body.discard()
            player.sendSystemMessage(Component.literal("§e[AEONIS] Returned to your body."))
        } else {
            player.sendSystemMessage(Component.literal("§c[AEONIS] Body not found — respawning at world spawn."))
            teleportToWorldSpawn(player)
        }
    }

    private fun loadInventoryFromMob(player: ServerPlayer, mob: LivingEntity) {
        // If mob has saved inventory, load it into player
        val stored = mobInventories[mob.id]
        if (stored != null) {
            for (i in stored.indices) {
                val s = stored[i]
                if (s != null && i < player.inventory.containerSize) {
                    player.inventory.setItem(i, s.copy())
                }
            }
        } else {
            // No stored inventory — transfer mob equipment to player slots
            player.inventory.setItem(0, mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).copy())
            // Offhand
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).copy())
            // Armor
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy())
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).copy())
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).copy())
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).copy())
        }
    }

    private fun saveInventoryToMob(player: ServerPlayer, mob: LivingEntity) {
        val size = player.inventory.containerSize
        val arr = Array<ItemStack?>(size) { null }
        for (i in 0 until size) {
            val s = player.inventory.getItem(i)
            arr[i] = s?.copy()
        }
        mobInventories[mob.id] = arr
    }

    private fun saveHeldBlockToEnderman(player: ServerPlayer, mob: LivingEntity) {
        // Attempt to copy player's mainhand block state to an Enderman's carried block using reflection where needed
        try {
            val main = player.mainHandItem.item
            // If player holds a block item, try to extract its block state
            if (main !is net.minecraft.world.item.BlockItem) return
            val state = main.block.defaultBlockState()

            // Try known EnderMan APIs
            if (mob is net.minecraft.world.entity.monster.EnderMan) {
                try {
                    // setCarriedBlock or setCarried
                    val setMethod = mob::class.java.methods.firstOrNull { it.name.contains("carri") && it.parameterCount == 1 }
                    if (setMethod != null) {
                        setMethod.invoke(mob, state)
                        return
                    }
                } catch (_: Exception) {}

                // Fallback: try reflection to set private field
                for (field in mob::class.java.declaredFields) {
                    if (field.type == net.minecraft.world.level.block.state.BlockState::class.java) {
                        field.isAccessible = true
                        try { field.set(mob, state); return } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {
            // best-effort, ignore
        }
    }

    private fun teleportToWorldSpawn(player: ServerPlayer) {
        // Fallback simple spawn if reliable spawn API is unknown in mappings: teleport to overworld high point
        player.teleportTo(0.5, 100.0, 0.5)
    }

    // Player connection helpers (extracted from QCmod)
    fun onPlayerJoin(player: ServerPlayer, server: net.minecraft.server.MinecraftServer) {
        // Broadcast controlling players so clients update UI
        AeonisNetworking.broadcastControllingPlayers(server)

        val pid = player.uuid
        
        // Check if player is NOT in possession mode but has incorrect health settings
        // This fixes the bug where player rejoins with mob's health after disconnect
        if (!AeonisNetworking.isPlayerTransformed(pid)) {
            // Reset max health to 20 if it's set to something else
            val maxHealthAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            if (maxHealthAttr != null && maxHealthAttr.baseValue != 20.0) {
                maxHealthAttr.baseValue = 20.0
                // Cap current health to new max
                if (player.health > 20.0f) {
                    player.health = 20.0f
                }
            }
            // Reset noPhysics and invisibility
            try {
                player.noPhysics = false
                player.isInvisible = false
            } catch (_: Exception) {}
        }
        
        if (activePossessions.getOrDefault(pid, false)) {
            val body = playerBodies[pid]
            val valid = body != null && body.isAlive && body.getOwnerUuid() == pid
            if (!valid) {
                playerBodies.remove(pid)
                activePossessions.remove(pid)
                bodyPositions.remove(pid)
                inventoryBackups.remove(pid)

                // Restore original gamemode if available
                val original = originalGameModes.remove(pid)
                if (original != null) {
                    player.setGameMode(original)
                }
                
                // Reset health to default
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue = 20.0
                if (player.health > 20.0f) {
                    player.health = 20.0f
                }
            }
        }
    }

    fun onPlayerDisconnect(player: ServerPlayer, server: net.minecraft.server.MinecraftServer) {
        val pid = player.uuid

        // If the player was controlling a mob, save their held items into the mob
        val controlledId = AeonisNetworking.getControlledEntityId(pid)
        if (controlledId != null) {
            val entity = player.level().getEntity(controlledId)
            if (entity is LivingEntity) {
                saveInventoryToMob(player, entity)
                try {
                    mobLastSelectedSlot[entity.id] = player.inventory.selectedSlot
                } catch (_: Exception) {}

                // If the controlled mob is an Enderman, try to store player's held block into it
                try {
                    saveHeldBlockToEnderman(player, entity)
                } catch (_: Exception) {}
            }
            AeonisNetworking.removeControlledEntity(player)
            try { player.isInvisible = false } catch (_: Exception) {}
            // Broadcast updated controlling players
            server.execute { AeonisNetworking.broadcastControllingPlayers(server) }
        }

        // If the player had an active body, restore their saved inventory and cleanup
        if (activePossessions.getOrDefault(pid, false)) {
            val body = playerBodies.remove(pid)
            activePossessions.remove(pid)
            bodyPositions.remove(pid)
            val backup = inventoryBackups.remove(pid)
            if (backup != null) {
                for (i in backup.indices) {
                    val s = backup[i]
                    if (s != null) player.inventory.setItem(i, s)
                }
            }
            val original = originalGameModes.remove(pid)
            if (original != null) player.setGameMode(original)
            
            // IMPORTANT: Reset player's max health and health to default (20)
            // This prevents the bug where player keeps mob's health after reconnect
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue = 20.0
            val bodyHealth = body?.health ?: 20.0f
            player.health = bodyHealth.coerceAtMost(20.0f)
        }
        
        // Reset noPhysics flag
        try { player.noPhysics = false } catch (_: Exception) {}
    }
}
