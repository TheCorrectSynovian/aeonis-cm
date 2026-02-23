package com.qc.aeonis.minigame.prophunt

import com.qc.aeonis.util.playNotifySound

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import org.slf4j.LoggerFactory
import kotlin.math.max
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      PROP HUNT MANAGER - CORE SYSTEM                       ║
 * ║                                                                            ║
 * ║  Central controller for all Prop Hunt minigame logic. Manages game state,  ║
 * ║  rounds, teams, scoring, and coordinates between all subsystems.           ║
 * ║                                                                            ║
 * ║  Design Philosophy:                                                        ║
 * ║  - Modular: Each system (props, hunters, arenas) is independent            ║
 * ║  - Performant: Uses efficient data structures and batch operations         ║
 * ║  - Expandable: Easy to add new features without breaking existing ones     ║
 * ║  - Minecraft-Native: Uses scoreboard API, vanilla mechanics, particles     ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropHuntManager {
    private val logger = LoggerFactory.getLogger("aeonis-prophunt")
    
    // ════════════════════════════════════════════════════════════════════════
    // GAME STATE TRACKING
    // ════════════════════════════════════════════════════════════════════════
    
    /** All active Prop Hunt games indexed by arena ID */
    private val activeGames = ConcurrentHashMap<String, PropHuntGame>()
    
    /** Player UUID -> Game ID mapping for quick lookups */
    private val playerGameMap = ConcurrentHashMap<UUID, String>()
    
    /** Global game statistics for leaderboards */
    val statistics = PropHuntStatistics()
    
    /** Configuration for Prop Hunt games */
    val config = PropHuntConfig()
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Registers Prop Hunt tick handler and event listeners.
     * Called during mod initialization.
     */
    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickAllGames(server)
        }
        logger.info("§a[PropHunt] §7Manager initialized and ready!")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // GAME LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new Prop Hunt game in the specified arena.
     * 
     * @param arenaId Unique identifier for the arena
     * @param level The server level where the game will take place
     * @param creator The player who created the game
     * @return The created game, or null if creation failed
     */
    fun createGame(arenaId: String, level: ServerLevel, creator: ServerPlayer): PropHuntGame? {
        if (activeGames.containsKey(arenaId)) {
            creator.sendSystemMessage(Component.literal("§c[PropHunt] §7A game already exists in arena: $arenaId"))
            return null
        }
        
        // Get or create arena
        val arena = ArenaManager.getOrCreateArena(arenaId, level, creator.blockPosition())
        if (arena == null) {
            creator.sendSystemMessage(Component.literal("§c[PropHunt] §7Failed to create arena: $arenaId"))
            return null
        }
        
        val game = PropHuntGame(
            id = "${arenaId}_${System.currentTimeMillis()}",
            arenaId = arenaId,
            arena = arena,
            level = level,
            settings = config.defaultSettings.copy()
        )
        
        activeGames[arenaId] = game
        logger.info("§a[PropHunt] §7Created new game in arena: $arenaId")
        
        broadcastToAll(level.server!!, "§e§l⚡ §6A new Prop Hunt game is starting in arena: §e$arenaId§6! Use §a/prophunt join $arenaId§6 to join!")
        
        return game
    }
    
    /**
     * Attempts to add a player to a game.
     * Handles team assignment and state synchronization.
     */
    fun joinGame(player: ServerPlayer, arenaId: String): Boolean {
        val game = activeGames[arenaId]
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7No game found in arena: $arenaId"))
            return false
        }
        
        // Check if player is already in a game
        val existingGameId = playerGameMap[player.uuid]
        if (existingGameId != null) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7You're already in a game! Use §e/prophunt leave§7 first."))
            return false
        }
        
        // Check game state
        if (game.state != GameState.WAITING && game.state != GameState.STARTING) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7This game has already started. Wait for the next round!"))
            return false
        }
        
        // Check player limit
        if (game.players.size >= game.settings.maxPlayers) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7This game is full! (${game.players.size}/${game.settings.maxPlayers})"))
            return false
        }
        
        // Add player to game
        game.addPlayer(player)
        playerGameMap[player.uuid] = arenaId
        
        // Setup scoreboard team
        setupPlayerScoreboard(player, game)
        
        // Teleport to lobby
        ArenaManager.teleportToLobby(player, game.arena)
        
        // Announce join
        game.broadcast("§a+ §e${player.name.string} §7joined the game! (${game.players.size}/${game.settings.maxPlayers})")
        
        // Auto-start check
        if (game.players.size >= game.settings.minPlayers && game.state == GameState.WAITING) {
            startCountdown(game)
        }
        
        return true
    }
    
    /**
     * Removes a player from their current game.
     */
    fun leaveGame(player: ServerPlayer): Boolean {
        val arenaId = playerGameMap[player.uuid]
        if (arenaId == null) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7You're not in any game!"))
            return false
        }
        
        val game = activeGames[arenaId]
        if (game != null) {
            val playerData = game.players[player.uuid]

            // Clean player state before removing game data.
            PropDisguiseManager.undisguise(player)
            HunterAbilityManager.resetHunter(player)
            clearPlayerScoreboard(player, game)
            playerData?.restoreState(player)

            game.removePlayer(player)
            game.broadcast("§c- §e${player.name.string} §7left the game. (${game.players.size}/${game.settings.maxPlayers})")
            checkGameIntegrity(game)
        }
        
        playerGameMap.remove(player.uuid)
        player.sendSystemMessage(Component.literal("§a[PropHunt] §7You left the game."))
        return true
    }
    
    /**
     * Force-ends a game and cleans up all state.
     */
    fun endGame(arenaId: String, reason: String = "Game ended") {
        val game = activeGames[arenaId] ?: return
        
        game.broadcast("§c§l⚠ §7Game ended: $reason")
        
        // Cleanup all players
        for (playerData in game.players.values.toList()) {
            val player = game.level.server?.playerList?.getPlayer(playerData.uuid)
            if (player != null) {
                PropDisguiseManager.undisguise(player)
                clearPlayerScoreboard(player, game)
                // Restore player state
                playerData.restoreState(player)
            }
            playerGameMap.remove(playerData.uuid)
        }
        
        // Clear game state
        game.cleanup()
        activeGames.remove(arenaId)
        
        logger.info("§a[PropHunt] §7Game in arena $arenaId ended: $reason")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ROUND MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Starts the pre-game countdown.
     */
    private fun startCountdown(game: PropHuntGame) {
        if (game.state == GameState.STARTING) return
        game.state = GameState.STARTING
        game.countdownTicks = game.settings.startCountdownSeconds * 20
        game.broadcast("§e§l⏱ §6Game starting in §e${game.settings.startCountdownSeconds}§6 seconds!")
    }
    
    /**
     * Begins a new round with team assignment and hiding phase.
     */
    fun startRound(game: PropHuntGame) {
        if (game.players.size < 2) {
            game.state = GameState.WAITING
            game.broadcast("§c[PropHunt] §7Not enough players to start the round.")
            return
        }

        game.currentRound++
        game.state = GameState.HIDING
        game.roundTicks = 0
        game.hideTimeTicks = game.settings.hideTimeSeconds * 20
        
        // Balance and assign teams
        TeamBalancer.balanceTeams(game)
        if (game.getHunters().isEmpty() || game.getProps().isEmpty()) {
            game.currentRound--
            game.state = GameState.WAITING
            game.broadcast("§c[PropHunt] §7Team balancing failed (need at least 1 hunter and 1 prop).")
            return
        }
        
        // Setup props
        for (propData in game.getProps()) {
            val player = game.level.server?.playerList?.getPlayer(propData.uuid) ?: continue
            PropDisguiseManager.assignRandomDisguise(player, game)
            ArenaManager.teleportToRandomSpawn(player, game.arena, PropHuntTeam.PROP)
            PropDisguiseManager.showPropTips(player)
        }
        
        // Setup hunters (frozen during hide time)
        for (hunterData in game.getHunters()) {
            val player = game.level.server?.playerList?.getPlayer(hunterData.uuid) ?: continue
            HunterAbilityManager.setupHunter(player, game)
            ArenaManager.teleportToRandomSpawn(player, game.arena, PropHuntTeam.HUNTER)
            HunterAbilityManager.freezeHunter(player, true)
        }
        
        game.broadcast("§a§l🎭 §bROUND ${game.currentRound} STARTED!")
        game.broadcast("§7Props have §e${game.settings.hideTimeSeconds}§7 seconds to hide!")
        game.broadcast("§cHunters are frozen and blinded!")
    }
    
    /**
     * Transitions from hiding phase to seeking phase.
     */
    fun startSeekingPhase(game: PropHuntGame) {
        game.state = GameState.SEEKING
        game.seekTimeTicks = game.settings.roundTimeSeconds * 20
        
        // Unfreeze hunters
        for (hunterData in game.getHunters()) {
            val player = game.level.server?.playerList?.getPlayer(hunterData.uuid) ?: continue
            HunterAbilityManager.freezeHunter(player, false)
            HunterAbilityManager.showHunterTips(player)
        }
        
        game.broadcast("§c§l🔍 §4HUNTERS RELEASED!")
        game.broadcast("§7Props: Stay hidden! Hunters: Find and eliminate all props!")
        game.broadcast("§7Round ends in §e${game.settings.roundTimeSeconds}§7 seconds!")
        
        // Play dramatic sound
        for (playerData in game.players.values) {
            val player = game.level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
            player.playNotifySound(
                net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
                net.minecraft.sounds.SoundSource.MASTER,
                0.5f, 1.2f
            )
        }
    }
    
    /**
     * Ends the current round and determines winner.
     */
    fun endRound(game: PropHuntGame, winner: PropHuntTeam, reason: String) {
        if (game.state == GameState.ROUND_END || game.state == GameState.ENDED) return

        game.state = GameState.ROUND_END
        game.roundEndTicks = game.settings.roundEndDelaySeconds * 20
        
        // Award points
        when (winner) {
            PropHuntTeam.PROP -> {
                game.propWins++
                for (propData in game.getProps()) {
                    propData.roundsWon++
                    statistics.recordPropWin(propData.uuid)
                }
                game.broadcast("§a§l🏆 §bPROPS WIN! §7$reason")
            }
            PropHuntTeam.HUNTER -> {
                game.hunterWins++
                for (hunterData in game.getHunters()) {
                    hunterData.roundsWon++
                    statistics.recordHunterWin(hunterData.uuid)
                }
                game.broadcast("§c§l🏆 §4HUNTERS WIN! §7$reason")
            }
            PropHuntTeam.NONE -> {
                game.broadcast("§e§l⚖ §6DRAW! §7$reason")
            }
        }
        
        // Show round summary
        showRoundSummary(game)
        PropHuntRewards.awardRoundRewards(game, winner)
        for (playerData in game.players.values) {
            val player = game.level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
            PropHuntAchievements.checkRoundEndAchievements(player, playerData, game)
        }
        
        // Check if game should end
        if (game.currentRound >= game.settings.maxRounds) {
            game.broadcast("§e§l════════════════════════")
            game.broadcast("§6§l  GAME OVER!")
            game.broadcast("§a  Props: §f${game.propWins} wins")
            game.broadcast("§c  Hunters: §f${game.hunterWins} wins")
            if (game.propWins > game.hunterWins) {
                game.broadcast("§a§l  PROPS ARE THE CHAMPIONS!")
            } else if (game.hunterWins > game.propWins) {
                game.broadcast("§c§l  HUNTERS ARE THE CHAMPIONS!")
            } else {
                game.broadcast("§e§l  IT'S A TIE!")
            }
            game.broadcast("§e§l════════════════════════")
            
            // Schedule game end
            game.gameEndTicks = 100 // 5 seconds
        }
    }
    
    /**
     * Prepares the next round.
     */
    fun prepareNextRound(game: PropHuntGame) {
        game.state = GameState.WAITING
        
        // Reset all players
        for (playerData in game.players.values) {
            val player = game.level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
            PropDisguiseManager.undisguise(player)
            HunterAbilityManager.resetHunter(player)
            playerData.resetRoundState()
        }
        
        game.broadcast("§e§l⏱ §6Next round starting in §e${game.settings.startCountdownSeconds}§6 seconds!")
        startCountdown(game)
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TICK HANDLING
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Main tick handler - updates all active games.
     */
    private fun tickAllGames(server: MinecraftServer) {
        for ((arenaId, game) in activeGames) {
            try {
                tickGame(game)
            } catch (e: Exception) {
                logger.error("§c[PropHunt] §7Error ticking game in arena $arenaId: ${e.message}")
            }
        }
    }
    
    /**
     * Ticks a single game instance.
     */
    private fun tickGame(game: PropHuntGame) {
        when (game.state) {
            GameState.WAITING -> {
                // Waiting for players - show tips periodically
                if (game.roundTicks % 200 == 0 && game.players.isNotEmpty()) {
                    val needed = game.settings.minPlayers - game.players.size
                    if (needed > 0) {
                        game.broadcast("§7Waiting for §e$needed§7 more player(s) to start...")
                    }
                }
                game.roundTicks++
            }
            
            GameState.STARTING -> {
                val onlinePlayers = game.players.values.count { game.level.server?.playerList?.getPlayer(it.uuid) != null }
                if (onlinePlayers < game.settings.minPlayers) {
                    game.state = GameState.WAITING
                    game.countdownTicks = 0
                    game.broadcast("§c[PropHunt] §7Countdown cancelled - waiting for more players.")
                    return
                }

                game.countdownTicks--
                
                // Countdown announcements
                val seconds = game.countdownTicks / 20
                if (game.countdownTicks % 20 == 0 && seconds in 1..10) {
                    game.broadcast("§e§l⏱ §6Starting in §e$seconds§6...")
                    
                    // Play tick sound
                    for (playerData in game.players.values) {
                        val player = game.level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
                        player.playNotifySound(
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),
                            net.minecraft.sounds.SoundSource.MASTER,
                            0.5f, 1.0f + (10 - seconds) * 0.1f
                        )
                    }
                }
                
                if (game.countdownTicks <= 0) {
                    startRound(game)
                }
            }
            
            GameState.HIDING -> {
                game.hideTimeTicks--
                game.roundTicks++
                
                // Countdown announcements
                val seconds = game.hideTimeTicks / 20
                if (game.hideTimeTicks % 20 == 0 && seconds in listOf(30, 20, 10, 5, 4, 3, 2, 1)) {
                    game.broadcast("§7Hunters released in §e$seconds§7 seconds!")
                }
                
                // Tick props (rotation lock, freeze, taunt)
                for (propData in game.getProps()) {
                    val player = game.level.server?.playerList?.getPlayer(propData.uuid) ?: continue
                    PropDisguiseManager.tickProp(player, game)
                    if (propData.isAlive) {
                        ArenaManager.enforceBorders(player, game.arena)
                    }
                }
                
                if (game.hideTimeTicks <= 0) {
                    startSeekingPhase(game)
                }
            }
            
            GameState.SEEKING -> {
                game.seekTimeTicks--
                game.roundTicks++
                
                // Time warnings
                val seconds = game.seekTimeTicks / 20
                if (game.seekTimeTicks % 20 == 0 && seconds in listOf(60, 30, 15, 10, 5, 4, 3, 2, 1)) {
                    game.broadcast("§7Time remaining: §e$seconds§7 seconds!")
                }
                
                // Tick props
                for (propData in game.getProps()) {
                    val player = game.level.server?.playerList?.getPlayer(propData.uuid) ?: continue
                    PropDisguiseManager.tickProp(player, game)
                    if (propData.isAlive) {
                        ArenaManager.enforceBorders(player, game.arena)
                    }
                }
                
                // Tick hunters
                for (hunterData in game.getHunters()) {
                    val player = game.level.server?.playerList?.getPlayer(hunterData.uuid) ?: continue
                    HunterAbilityManager.tickHunter(player, game)
                    if (hunterData.isAlive) {
                        ArenaManager.enforceBorders(player, game.arena)
                    }
                }
                
                // Periodic hunter hints
                val hintIntervalTicks = max(10, if (game.seekTimeTicks <= 60 * 20) {
                    (game.settings.hintIntervalSeconds * 10)
                } else {
                    (game.settings.hintIntervalSeconds * 20)
                })
                if (game.settings.hunterHintsEnabled && game.seekTimeTicks % hintIntervalTicks == 0) {
                    HunterAbilityManager.giveHint(game)
                }
                
                // Check win conditions
                checkGameEndConditions(game)
                
                // Time's up - props win
                if (game.seekTimeTicks <= 0) {
                    endRound(game, PropHuntTeam.PROP, "Time ran out!")
                }
            }
            
            GameState.ROUND_END -> {
                game.roundEndTicks--
                
                if (game.roundEndTicks <= 0) {
                    if (game.gameEndTicks > 0) {
                        game.gameEndTicks--
                        if (game.gameEndTicks <= 0) {
                            endGame(game.arenaId, "Game completed!")
                        }
                    } else if (game.currentRound < game.settings.maxRounds) {
                        prepareNextRound(game)
                    }
                }
            }
            
            GameState.ENDED -> {
                // Cleanup will be handled by endGame
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // WIN CONDITION CHECKS
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if the game should end and determines the winner.
     */
    private fun checkGameEndConditions(game: PropHuntGame) {
        if (game.state != GameState.SEEKING) return
        
        val aliveProps = game.getProps().count { it.isAlive && game.level.server?.playerList?.getPlayer(it.uuid) != null }
        val aliveHunters = game.getHunters().count { it.isAlive && game.level.server?.playerList?.getPlayer(it.uuid) != null }
        
        // All props eliminated
        if (aliveProps == 0) {
            endRound(game, PropHuntTeam.HUNTER, "All props have been found!")
            return
        }
        
        // All hunters eliminated (rare, but possible with taunt damage)
        if (aliveHunters == 0) {
            endRound(game, PropHuntTeam.PROP, "All hunters have been eliminated!")
            return
        }
        
        // Not enough players to continue
        if (game.players.size < 2) {
            endGame(game.arenaId, "Not enough players!")
            return
        }
    }

    private fun checkGameIntegrity(game: PropHuntGame) {
        if (game.players.isEmpty()) {
            endGame(game.arenaId, "All players left.")
            return
        }

        if (game.state == GameState.SEEKING) {
            checkGameEndConditions(game)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // SCOREBOARD MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Sets up scoreboard teams and display for a player.
     */
    private fun setupPlayerScoreboard(player: ServerPlayer, game: PropHuntGame) {
        val server = (player.level() as ServerLevel).server
        val scoreboard = server.scoreboard
        
        // Create teams if they don't exist
        getOrCreateTeam(scoreboard, "ph_props", "§a§lProps", "§a")
        getOrCreateTeam(scoreboard, "ph_hunters", "§c§lHunters", "§c")
        getOrCreateTeam(scoreboard, "ph_spectators", "§7§lSpectators", "§7")
        
        // Initially add to spectator team
        val specTeam = scoreboard.getPlayerTeam("ph_spectators")
        if (specTeam != null) {
            scoreboard.addPlayerToTeam(player.scoreboardName, specTeam)
        }
    }
    
    /**
     * Updates a player's team on the scoreboard.
     */
    fun updatePlayerTeam(player: ServerPlayer, team: PropHuntTeam) {
        val scoreboard = (player.level() as ServerLevel).server.scoreboard

        // Remove from previous PropHunt teams first to avoid stale team state.
        for (teamName in listOf("ph_props", "ph_hunters", "ph_spectators")) {
            val existing = scoreboard.getPlayerTeam(teamName) ?: continue
            if (existing.players.contains(player.scoreboardName)) {
                scoreboard.removePlayerFromTeam(player.scoreboardName, existing)
            }
        }
        
        val teamName = when (team) {
            PropHuntTeam.PROP -> "ph_props"
            PropHuntTeam.HUNTER -> "ph_hunters"
            PropHuntTeam.NONE -> "ph_spectators"
        }
        
        val scoreTeam = scoreboard.getPlayerTeam(teamName)
        if (scoreTeam != null) {
            scoreboard.addPlayerToTeam(player.scoreboardName, scoreTeam)
        }
    }
    
    /**
     * Clears scoreboard data for a player.
     */
    private fun clearPlayerScoreboard(player: ServerPlayer, game: PropHuntGame) {
        val scoreboard = (player.level() as ServerLevel).server.scoreboard
        
        // Remove from all prop hunt teams
        for (teamName in listOf("ph_props", "ph_hunters", "ph_spectators")) {
            val team = scoreboard.getPlayerTeam(teamName)
            if (team != null && team.players.contains(player.scoreboardName)) {
                scoreboard.removePlayerFromTeam(player.scoreboardName, team)
            }
        }
    }
    
    /**
     * Creates or gets a scoreboard team.
     */
    private fun getOrCreateTeam(scoreboard: Scoreboard, name: String, displayName: String, color: String): PlayerTeam {
        var team = scoreboard.getPlayerTeam(name)
        if (team == null) {
            team = scoreboard.addPlayerTeam(name)
            team.setPlayerPrefix(Component.literal(color))
            team.setDisplayName(Component.literal(displayName))
            team.isAllowFriendlyFire = false
            team.setSeeFriendlyInvisibles(true)
        }
        return team
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the game a player is currently in.
     */
    fun getPlayerGame(player: ServerPlayer): PropHuntGame? {
        val arenaId = playerGameMap[player.uuid] ?: return null
        return activeGames[arenaId]
    }
    
    /**
     * Gets player data for a player in their current game.
     */
    fun getPlayerData(player: ServerPlayer): PropHuntPlayerData? {
        val game = getPlayerGame(player) ?: return null
        return game.players[player.uuid]
    }
    
    /**
     * Lists all active games.
     */
    fun listGames(): List<PropHuntGame> = activeGames.values.toList()
    
    /**
     * Shows the round summary with statistics.
     */
    private fun showRoundSummary(game: PropHuntGame) {
        game.broadcast("§e§l═══════ ROUND SUMMARY ═══════")
        
        // Top prop (survived longest or never found)
        val topProp = game.getProps()
            .filter { it.isAlive }
            .maxByOrNull { it.survivalTicks }
        if (topProp != null) {
            val player = game.level.server?.playerList?.getPlayer(topProp.uuid)
            game.broadcast("§a§lBest Hider: §f${player?.name?.string ?: "Unknown"}")
        }
        
        // Top hunter
        val topHunter = game.getHunters()
            .maxByOrNull { it.propsFound }
        if (topHunter != null && topHunter.propsFound > 0) {
            val player = game.level.server?.playerList?.getPlayer(topHunter.uuid)
            game.broadcast("§c§lTop Hunter: §f${player?.name?.string ?: "Unknown"} §7(${topHunter.propsFound} found)")
        }
        
        game.broadcast("§e§l═════════════════════════════")
    }
    
    /**
     * Broadcasts a message to all players on the server.
     */
    private fun broadcastToAll(server: MinecraftServer, message: String) {
        for (player in server.playerList.players) {
            player.sendSystemMessage(Component.literal(message))
        }
    }
    
    /**
     * Called when a player is hit/attacked.
     * Returns true if the attack should be processed.
     */
    fun onPlayerAttacked(attacker: ServerPlayer, victim: ServerPlayer): Boolean {
        val attackerGame = getPlayerGame(attacker) ?: return true
        val victimGame = getPlayerGame(victim) ?: return true
        
        // Must be in the same game
        if (attackerGame.id != victimGame.id) return true
        
        // Only process during seeking phase
        if (attackerGame.state != GameState.SEEKING) return false
        
        val attackerData = attackerGame.players[attacker.uuid] ?: return true
        val victimData = attackerGame.players[victim.uuid] ?: return true
        
        // Hunter attacking prop
        if (attackerData.team == PropHuntTeam.HUNTER && victimData.team == PropHuntTeam.PROP) {
            if (victimData.isAlive) {
                // Prop found!
                PropDisguiseManager.onPropCaught(victim, attacker, attackerGame)
                return false // Cancel vanilla damage, we handle it
            }
        }
        
        // Prop attacking hunter (taunts can deal damage)
        if (attackerData.team == PropHuntTeam.PROP && victimData.team == PropHuntTeam.HUNTER) {
            // Props can't directly attack, but taunts might cause effects
            return false
        }
        
        return true
    }
    
    /**
     * Called when a player disconnects.
     */
    fun onPlayerDisconnect(player: ServerPlayer) {
        val arenaId = playerGameMap[player.uuid] ?: return
        val game = activeGames[arenaId] ?: return
        
        val playerData = game.players[player.uuid]
        if (playerData != null) {
            // Mark as eliminated if in game
            if (game.state == GameState.SEEKING || game.state == GameState.HIDING) {
                playerData.isAlive = false
                game.broadcast("§c✖ §e${player.name.string} §7disconnected and has been eliminated!")
            }
        }

        PropDisguiseManager.removeDisguise(player)
        HunterAbilityManager.resetHunter(player)
        clearPlayerScoreboard(player, game)
        
        game.removePlayer(player)
        playerGameMap.remove(player.uuid)
        
        checkGameIntegrity(game)
    }
}
