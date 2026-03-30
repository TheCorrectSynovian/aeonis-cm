package com.qc.aeonis.minigame.manhunt

import com.qc.aeonis.entity.AeonisEntities
import com.qc.aeonis.entity.HunterEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      MANHUNT MANAGER - CORE SYSTEM                         ║
 * ║                                                                            ║
 * ║  Central controller for all Manhunt minigame logic. Manages game state,    ║
 * ║  hunter AI, player tracking, and coordinates between all subsystems.       ║
 * ║                                                                            ║
 * ║  Inspired by Dream's Minecraft Manhunt!                                    ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object ManhuntManager {
    private val logger = LoggerFactory.getLogger("aeonis-manhunt")
    
    // ════════════════════════════════════════════════════════════════════════
    // GAME STATE TRACKING
    // ════════════════════════════════════════════════════════════════════════
    
    /** All active Manhunt games indexed by game ID */
    private val activeGames = ConcurrentHashMap<String, ManhuntGame>()
    
    /** Player UUID -> Game ID mapping for quick lookups */
    private val playerGameMap = ConcurrentHashMap<UUID, String>()
    
    /** Hunter entity ID -> Game ID mapping */
    private val hunterGameMap = ConcurrentHashMap<Int, String>()
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Registers Manhunt tick handler and event listeners.
     * Called during mod initialization.
     */
    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickAllGames(server)
        }
        logger.info("§a[Manhunt] §7Manager initialized and ready!")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // GAME LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new Manhunt game.
     */
    fun createGame(level: ServerLevel, creator: ServerPlayer, settings: ManhuntSettings): ManhuntGame? {
        // Check if creator is already in a game
        if (playerGameMap.containsKey(creator.uuid)) {
            creator.sendSystemMessage(Component.literal("§c[Manhunt] §7You're already in a game! Use §e/manhunt leave§7 first."))
            return null
        }
        
        val gameId = "manhunt_${System.currentTimeMillis()}"
        
        val game = ManhuntGame(
            id = gameId,
            level = level,
            settings = settings,
            creatorUUID = creator.uuid
        )
        
        game.spawnPos = creator.blockPosition()
        game.hunterRespawnPos = game.spawnPos
        
        activeGames[gameId] = game
        logger.info("§a[Manhunt] §7Created new game: $gameId by ${creator.name.string}")
        
        return game
    }
    
    /**
     * Gets a game by ID.
     */
    fun getGame(gameId: String): ManhuntGame? = activeGames[gameId]
    
    /**
     * Gets the game a player is in.
     */
    fun getPlayerGame(playerUUID: UUID): ManhuntGame? {
        val gameId = playerGameMap[playerUUID] ?: return null
        return activeGames[gameId]
    }
    
    /**
     * Attempts to add a player to a game as speedrunner.
     */
    fun joinGame(player: ServerPlayer, gameId: String): Boolean {
        val game = activeGames[gameId]
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7No game found with ID: $gameId"))
            return false
        }
        
        // Check if player is already in a game
        if (playerGameMap.containsKey(player.uuid)) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're already in a game! Use §e/manhunt leave§7 first."))
            return false
        }
        
        // Check game state
        if (game.state != ManhuntGameState.SETUP) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7This game has already started!"))
            return false
        }
        
        // Add player to game
        game.addSpeedrunner(player)
        playerGameMap[player.uuid] = gameId
        
        game.broadcast("§a+ §e${player.name.string} §7joined as a speedrunner! (${game.players.size} players)")
        
        return true
    }
    
    /**
     * Removes a player from their current game.
     */
    fun leaveGame(player: ServerPlayer): Boolean {
        val gameId = playerGameMap[player.uuid]
        if (gameId == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return false
        }
        
        val game = activeGames[gameId]
        if (game != null) {
            // If creator leaves, end the game to avoid orphaned game control/state.
            if (game.creatorUUID == player.uuid) {
                endGame(gameId, "Game creator left")
                playerGameMap.remove(player.uuid)
                player.sendSystemMessage(Component.literal("§a[Manhunt] §7You left the game."))
                return true
            }

            val playerData = game.players[player.uuid]
            
            // Restore player state if game was active
            if (game.state != ManhuntGameState.SETUP && playerData != null) {
                playerData.restoreState(player)
            }
            
            game.removePlayer(player)
            game.broadcast("§c- §e${player.name.string} §7left the game. (${game.players.size} players)")
            
            // Check if game should end
            if (game.state == ManhuntGameState.ACTIVE || game.state == ManhuntGameState.HEAD_START) {
                checkWinConditions(game)
            }
            
            // End game if no players left
            if (game.players.isEmpty()) {
                endGame(gameId, "All players left")
            }
        }
        
        playerGameMap.remove(player.uuid)
        player.sendSystemMessage(Component.literal("§a[Manhunt] §7You left the game."))
        return true
    }
    
    /**
     * Starts a manhunt game.
     */
    fun startGame(gameId: String): Boolean {
        val game = activeGames[gameId] ?: return false
        
        if (game.state != ManhuntGameState.SETUP) {
            return false
        }
        
        if (game.players.isEmpty()) {
            return false
        }
        
        val level = game.level
        val spawnPos = game.spawnPos ?: return false
        
        // Start countdown
        game.state = ManhuntGameState.COUNTDOWN
        game.countdownTicks = 100 // 5 second countdown
        
        game.broadcastTitle("§6§lMANHUNT", "§eGet ready...")
        game.broadcast("§6§l════════════════════════════════")
        game.broadcast("§e§lMANHUNT STARTING!")
        game.broadcast("§7Difficulty: ${game.getDifficultyName()}")
        game.broadcast("§7Head Start: §e${game.settings.headStartSeconds}s")
        game.broadcast("§7Speedrunners: §a${game.players.size}")
        game.broadcast("§6§l════════════════════════════════")
        
        return true
    }
    
    /**
     * Actually starts the game after countdown.
     */
    private fun beginGame(game: ManhuntGame) {
        val level = game.level
        val spawnPos = game.spawnPos ?: return
        
        // Spawn the hunter
        val hunter = spawnHunter(level, spawnPos, game)
        if (hunter == null) {
            game.broadcast("§c[Manhunt] §7Failed to spawn hunter! Game cancelled.")
            endGame(game.id, "Hunter spawn failed")
            return
        }
        
        game.hunterEntityId = hunter.id
        hunterGameMap[hunter.id] = game.id
        
        // Freeze hunter during head start
        hunter.freeze()
        
        // Setup all speedrunners
        for (playerData in game.getSpeedrunners()) {
            val player = level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
            
            // Set survival mode
            player.setGameMode(GameType.SURVIVAL)
            
            // Clear inventory
            player.inventory.clearContent()
            
            // Give tracking compass if enabled
            if (game.settings.compassEnabled) {
                giveTrackingCompass(player)
            }
            
            // Teleport to spawn
            player.teleportTo(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5)
        }
        
        // Set game state
        game.state = ManhuntGameState.HEAD_START
        game.headStartTicks = game.settings.headStartSeconds * 20
        game.startTime = level.gameTime
        
        // Announce
        game.broadcastTitle("§a§lGO!", "§eYou have ${game.settings.headStartSeconds}s head start!")
        game.broadcast("§a§l▶ The hunt begins! You have §e${game.settings.headStartSeconds} seconds §abefore the Hunter is released!")
        
        level.playSound(null, spawnPos, SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 1.0f, 1.0f)
        
        logger.info("§a[Manhunt] §7Game ${game.id} started with ${game.players.size} players")
    }
    
    /**
     * Spawns the hunter for a game.
     */
    private fun spawnHunter(level: ServerLevel, pos: BlockPos, game: ManhuntGame): HunterEntity? {
        val hunter = HunterEntity.spawnHunter(level, pos, game.id, game.settings.difficulty)
        
        if (hunter != null) {
            // Give hunter starting blocks
            hunter.giveBlocks(game.settings.hunterStartBlocks)
            hunter.setPrimarySpawn(pos)
            game.hunterRespawnPos = pos
        }
        
        return hunter
    }
    
    /**
     * Releases the hunter after head start.
     */
    private fun releaseHunter(game: ManhuntGame) {
        val level = game.level
        val hunterId = game.hunterEntityId ?: return
        
        val hunter = level.getEntity(hunterId) as? HunterEntity ?: return
        
        // Unfreeze and start hunting
        hunter.unfreeze()
        hunter.startHunting()
        
        game.state = ManhuntGameState.ACTIVE
        game.hunterReleaseTime = level.gameTime
        
        // Dramatic announcement
        game.broadcastTitle("§c§l⚠ HUNTER RELEASED ⚠", "§4Run for your life!")
        game.broadcast("§c§l════════════════════════════════")
        game.broadcast("§4§l☠ THE HUNTER HAS BEEN RELEASED! ☠")
        game.broadcast("§c§l════════════════════════════════")
        
        level.playSound(null, hunter.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.5f)
    }
    
    /**
     * Force-ends a game.
     */
    fun endGame(gameId: String, reason: String = "Game ended") {
        val game = activeGames[gameId] ?: return
        
        game.state = ManhuntGameState.ENDED
        
        game.broadcast("§6§l════════════════════════════════")
        game.broadcast("§e§lGAME OVER!")
        game.broadcast("§7Reason: $reason")
        game.broadcast("§7Duration: §e${game.getElapsedTimeFormatted()}")
        game.broadcast("§6§l════════════════════════════════")
        
        // Cleanup hunter
        val hunterId = game.hunterEntityId
        if (hunterId != null) {
            val hunter = game.level.getEntity(hunterId)
            hunter?.discard()
            hunterGameMap.remove(hunterId)
            HunterEntity.removeHunterForGame(gameId)
        }
        
        // Cleanup all players
        for (playerData in game.players.values.toList()) {
            val player = game.level.server?.playerList?.getPlayer(playerData.uuid)
            if (player != null) {
                playerData.restoreState(player)
            }
            playerGameMap.remove(playerData.uuid)
        }
        
        activeGames.remove(gameId)
        
        logger.info("§a[Manhunt] §7Game $gameId ended: $reason")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // GAME TICK
    // ════════════════════════════════════════════════════════════════════════
    
    private fun tickAllGames(server: MinecraftServer) {
        for (game in activeGames.values.toList()) {
            tickGame(game, server)
        }
    }
    
    private fun tickGame(game: ManhuntGame, server: MinecraftServer) {
        when (game.state) {
            ManhuntGameState.COUNTDOWN -> tickCountdown(game)
            ManhuntGameState.HEAD_START -> tickHeadStart(game)
            ManhuntGameState.ACTIVE -> tickActive(game, server)
            else -> {}
        }
    }
    
    private fun tickCountdown(game: ManhuntGame) {
        game.countdownTicks--
        
        // Countdown announcements
        when (game.countdownTicks) {
            80 -> game.broadcastTitle("§e4", "")
            60 -> game.broadcastTitle("§e3", "")
            40 -> game.broadcastTitle("§c2", "")
            20 -> game.broadcastTitle("§41", "")
            0 -> beginGame(game)
        }
    }
    
    private fun tickHeadStart(game: ManhuntGame) {
        game.headStartTicks--
        game.elapsedTicks++
        
        // Countdown announcements
        val secondsLeft = game.headStartTicks / 20
        when (game.headStartTicks) {
            600 -> game.broadcast("§e⏰ 30 seconds until hunter release!")
            400 -> game.broadcast("§e⏰ 20 seconds until hunter release!")
            200 -> game.broadcast("§c⏰ 10 seconds until hunter release!")
            100 -> game.broadcastTitle("§c5", "§7Hunter releasing soon...")
            80 -> game.broadcastTitle("§c4", "")
            60 -> game.broadcastTitle("§c3", "")
            40 -> game.broadcastTitle("§42", "")
            20 -> game.broadcastTitle("§41", "")
            0 -> releaseHunter(game)
        }
        
        // Update compasses
        updateCompasses(game)
    }
    
    private fun tickActive(game: ManhuntGame, server: MinecraftServer) {
        game.elapsedTicks++

        ensureHunterPresence(game)
        
        // Update compasses periodically
        if (game.elapsedTicks % game.settings.compassUpdateInterval == 0) {
            updateCompasses(game)
        }
        
        // Check win conditions
        checkWinConditions(game)
        
        // Periodic status updates
        if (game.elapsedTicks % 1200 == 0) { // Every minute
            game.broadcast("§7[§eTime: ${game.getElapsedTimeFormatted()}§7] §aSpeedrunners alive: ${game.getAliveSpeedrunners().size}")
        }
    }

    private fun ensureHunterPresence(game: ManhuntGame) {
        val level = game.level
        val hunterId = game.hunterEntityId
        val hunter = hunterId?.let { level.getEntity(it) as? HunterEntity }

        if (hunter != null && hunter.isAlive) {
            game.hunterRespawnPos = hunter.getPreferredRespawnPos(level)
            return
        }

        val spawnPos = game.hunterRespawnPos ?: game.spawnPos ?: BlockPos(
            0,
            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, 0),
            0
        )
        val newHunter = spawnHunter(level, spawnPos, game) ?: return
        game.hunterEntityId = newHunter.id
        hunterGameMap[newHunter.id] = game.id
        if (hunterId != null) {
            hunterGameMap.remove(hunterId)
        }

        newHunter.startHunting()
        game.broadcast("§c⚔ The Hunter has respawned at its anchor point.")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // COMPASS TRACKING
    // ════════════════════════════════════════════════════════════════════════
    
    private fun giveTrackingCompass(player: ServerPlayer) {
        val compass = ItemStack(Items.COMPASS)
        compass.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, 
            Component.literal("§c§lHunter Tracker"))
        
        // Add lore
        val lore = listOf(
            Component.literal("§7Points to the Hunter's location"),
            Component.literal("§7Updates every few seconds")
        )
        
        player.inventory.add(compass)
    }
    
    private fun updateCompasses(game: ManhuntGame) {
        val hunterId = game.hunterEntityId ?: return
        val hunter = game.level.getEntity(hunterId) as? HunterEntity ?: return
        val hunterPos = hunter.blockPosition()
        
        for (playerData in game.getSpeedrunners()) {
            if (!playerData.isAlive) continue
            
            val player = game.level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
            
            // Find compass in inventory
            for (i in 0 until player.inventory.containerSize) {
                val stack = player.inventory.getItem(i)
                if (stack.item == Items.COMPASS) {
                    // Update compass to point to hunter
                    // In vanilla, lodestone compass points to a specific location
                    val tag = stack.getOrDefault(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY
                    )
                    
                    // Send particle trail hint towards hunter
                    val direction = hunterPos.center.subtract(player.position()).normalize()
                    val particlePos = player.position().add(direction.scale(2.0))
                    
                    game.level.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.END_ROD,
                        particlePos.x, particlePos.y + 1.0, particlePos.z,
                        1, 0.0, 0.0, 0.0, 0.0
                    )
                    
                    // Update action bar with distance
                    val distance = player.blockPosition().distSqr(hunterPos).let { kotlin.math.sqrt(it.toDouble()).toInt() }
                    player.sendSystemMessage(Component.literal("§c☠ Hunter: §e${distance} blocks away"))
                    
                    break
                }
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // WIN CONDITIONS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun checkWinConditions(game: ManhuntGame) {
        // Hunter wins if all speedrunners are dead
        if (game.areAllSpeedrunnersEliminated()) {
            hunterWins(game)
            return
        }
        
        // Check win condition
        when (game.settings.winCondition) {
            WinCondition.KILL_DRAGON -> {
                // Check if dragon is dead (would need to track this)
                // For now, check if any player is in the end with dragon dead
                for (playerUUID in game.getAliveSpeedrunners()) {
                    val player = game.level.server?.playerList?.getPlayer(playerUUID) ?: continue
                    val endLevel = game.level.server?.getLevel(net.minecraft.world.level.Level.END)
                    if (endLevel != null && player.level() == endLevel) {
                        // Check if dragon fight is complete
                        val dragonFight = endLevel.dragonFight
                        if (dragonFight?.hasPreviouslyKilledDragon() == true) {
                            speedrunnersWin(game)
                            return
                        }
                    }
                }
            }
            WinCondition.REACH_END -> {
                for (playerUUID in game.getAliveSpeedrunners()) {
                    val player = game.level.server?.playerList?.getPlayer(playerUUID) ?: continue
                    if (player.level().dimension() == net.minecraft.world.level.Level.END) {
                        speedrunnersWin(game)
                        return
                    }
                }
            }
            else -> {}
        }
    }
    
    private fun hunterWins(game: ManhuntGame) {
        game.broadcastTitle("§c§l☠ HUNTER WINS! ☠", "§7All speedrunners eliminated!")
        endGame(game.id, "§c☠ Hunter eliminated all speedrunners!")
    }
    
    private fun speedrunnersWin(game: ManhuntGame) {
        game.broadcastTitle("§a§l🏆 SPEEDRUNNERS WIN! 🏆", "§7The dragon has been slain!")
        endGame(game.id, "§a🏆 Speedrunners achieved victory!")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // PLAYER DEATH HANDLING
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Called when a speedrunner dies.
     */
    fun onPlayerDeath(player: ServerPlayer) {
        val game = getPlayerGame(player.uuid) ?: return
        val playerData = game.players[player.uuid] ?: return
        
        if (playerData.role != ManhuntRole.SPEEDRUNNER) return
        
        playerData.deathCount++
        
        if (game.settings.respawnEnabled && 
            (game.settings.maxRespawns < 0 || playerData.deathCount <= game.settings.maxRespawns)) {
            // Allow respawn
            val lifeInfo = if (game.settings.maxRespawns < 0) {
                "${playerData.deathCount}/∞ lives used"
            } else {
                "${playerData.deathCount}/${game.settings.maxRespawns} lives used"
            }
            game.broadcast("§c☠ §e${player.name.string} §7was killed! §e($lifeInfo)")
        } else {
            // Eliminate player
            game.eliminatePlayer(player.uuid)
            playerData.role = ManhuntRole.SPECTATOR
            
            game.broadcast("§4☠ §e${player.name.string} §7has been §celiminated§7!")
            
            // Set to spectator mode
            player.setGameMode(GameType.SPECTATOR)
            
            checkWinConditions(game)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // HUNTER MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the hunter for a player's game.
     */
    fun getHunterForPlayer(player: ServerPlayer): HunterEntity? {
        val game = getPlayerGame(player.uuid) ?: return null
        val hunterId = game.hunterEntityId ?: return null
        return game.level.getEntity(hunterId) as? HunterEntity
    }
    
    /**
     * Freezes the hunter (admin command).
     */
    fun freezeHunter(gameId: String): Boolean {
        val game = activeGames[gameId] ?: return false
        val hunterId = game.hunterEntityId ?: return false
        val hunter = game.level.getEntity(hunterId) as? HunterEntity ?: return false
        
        hunter.freeze()
        game.broadcast("§e⏸ The Hunter has been frozen by an admin.")
        return true
    }
    
    /**
     * Unfreezes the hunter (admin command).
     */
    fun unfreezeHunter(gameId: String): Boolean {
        val game = activeGames[gameId] ?: return false
        val hunterId = game.hunterEntityId ?: return false
        val hunter = game.level.getEntity(hunterId) as? HunterEntity ?: return false
        
        hunter.unfreeze()
        game.broadcast("§a▶ The Hunter has been unfrozen.")
        return true
    }
    
    /**
     * Gets all active game IDs.
     */
    fun getActiveGameIds(): List<String> = activeGames.keys.toList()
    
    /**
     * Gets a game by player.
     */
    fun getGameByPlayer(playerUUID: UUID): ManhuntGame? {
        val gameId = playerGameMap[playerUUID] ?: return null
        return activeGames[gameId]
    }
    
    /**
     * Broadcast to all servers.
     */
    fun broadcastToAll(server: MinecraftServer, message: String) {
        server.playerList.players.forEach { player ->
            player.sendSystemMessage(Component.literal(message))
        }
    }
}
