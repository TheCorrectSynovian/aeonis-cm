package com.qc.aeonis.minigame.prophunt

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                         PROP HUNT GAME INSTANCE                            ║
 * ║                                                                            ║
 * ║  Represents a single Prop Hunt game session. Tracks all game-specific      ║
 * ║  state including players, teams, rounds, and timing information.           ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
data class PropHuntGame(
    val id: String,
    val arenaId: String,
    val arena: PropHuntArena,
    val level: ServerLevel,
    val settings: PropHuntSettings
) {
    // ════════════════════════════════════════════════════════════════════════
    // GAME STATE
    // ════════════════════════════════════════════════════════════════════════
    
    /** Current game phase */
    var state: GameState = GameState.WAITING
    
    /** Current round number (1-indexed) */
    var currentRound: Int = 0
    
    /** Total props wins this game */
    var propWins: Int = 0
    
    /** Total hunter wins this game */
    var hunterWins: Int = 0
    
    // ════════════════════════════════════════════════════════════════════════
    // TIMING
    // ════════════════════════════════════════════════════════════════════════
    
    /** Ticks remaining in countdown phase */
    var countdownTicks: Int = 0
    
    /** Ticks elapsed in current round */
    var roundTicks: Int = 0
    
    /** Ticks remaining in hide phase */
    var hideTimeTicks: Int = 0
    
    /** Ticks remaining in seek phase */
    var seekTimeTicks: Int = 0
    
    /** Ticks remaining in round-end phase */
    var roundEndTicks: Int = 0
    
    /** Ticks until game ends (after final round) */
    var gameEndTicks: Int = 0
    
    // ════════════════════════════════════════════════════════════════════════
    // PLAYER MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /** All players in this game, keyed by UUID */
    val players = ConcurrentHashMap<UUID, PropHuntPlayerData>()
    
    /**
     * Adds a player to the game.
     */
    fun addPlayer(player: ServerPlayer) {
        val data = PropHuntPlayerData(
            uuid = player.uuid,
            originalGameMode = player.gameMode.gameModeForPlayer,
            originalPosition = player.blockPosition(),
            originalInventory = saveInventory(player)
        )
        players[player.uuid] = data
    }
    
    /**
     * Removes a player from the game.
     */
    fun removePlayer(player: ServerPlayer) {
        players.remove(player.uuid)
    }
    
    /**
     * Gets all players currently assigned to Props team.
     */
    fun getProps(): List<PropHuntPlayerData> {
        return players.values.filter { it.team == PropHuntTeam.PROP }
    }
    
    /**
     * Gets all players currently assigned to Hunters team.
     */
    fun getHunters(): List<PropHuntPlayerData> {
        return players.values.filter { it.team == PropHuntTeam.HUNTER }
    }
    
    /**
     * Gets all spectating players.
     */
    fun getSpectators(): List<PropHuntPlayerData> {
        return players.values.filter { it.team == PropHuntTeam.NONE || !it.isAlive }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // MESSAGING
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Broadcasts a message to all players in this game.
     */
    fun broadcast(message: String) {
        val component = Component.literal(message)
        for (playerData in players.values) {
            val player = level.server?.playerList?.getPlayer(playerData.uuid)
            player?.sendSystemMessage(component)
        }
    }
    
    /**
     * Sends a message only to Props team.
     */
    fun broadcastToProps(message: String) {
        val component = Component.literal(message)
        for (propData in getProps()) {
            val player = level.server?.playerList?.getPlayer(propData.uuid)
            player?.sendSystemMessage(component)
        }
    }
    
    /**
     * Sends a message only to Hunters team.
     */
    fun broadcastToHunters(message: String) {
        val component = Component.literal(message)
        for (hunterData in getHunters()) {
            val player = level.server?.playerList?.getPlayer(hunterData.uuid)
            player?.sendSystemMessage(component)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Saves a player's inventory for later restoration.
     */
    private fun saveInventory(player: ServerPlayer): Array<ItemStack> {
        val inv = player.inventory
        return Array(inv.containerSize) { i -> inv.getItem(i).copy() }
    }
    
    /**
     * Cleans up all game resources.
     */
    fun cleanup() {
        state = GameState.ENDED
        players.clear()
    }
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                           GAME STATE ENUM                                  ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
enum class GameState {
    /** Waiting for players to join */
    WAITING,
    
    /** Countdown before game starts */
    STARTING,
    
    /** Props are hiding, hunters are frozen */
    HIDING,
    
    /** Hunters are seeking props */
    SEEKING,
    
    /** Brief pause between rounds */
    ROUND_END,
    
    /** Game has concluded */
    ENDED
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                              TEAM ENUM                                     ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
enum class PropHuntTeam {
    PROP,    // Disguised players hiding
    HUNTER,  // Players searching for props
    NONE     // Spectator or unassigned
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                         PLAYER DATA CONTAINER                              ║
 * ║                                                                            ║
 * ║  Stores all game-specific data for a single player participating in        ║
 * ║  a Prop Hunt match. Includes state backups for proper restoration.         ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
data class PropHuntPlayerData(
    val uuid: UUID,
    val originalGameMode: GameType,
    val originalPosition: BlockPos,
    val originalInventory: Array<ItemStack>
) {
    // ════════════════════════════════════════════════════════════════════════
    // TEAM STATE
    // ════════════════════════════════════════════════════════════════════════
    
    /** Current team assignment */
    var team: PropHuntTeam = PropHuntTeam.NONE
    
    /** Whether this player is still "alive" (not caught/eliminated) */
    var isAlive: Boolean = true
    
    // ════════════════════════════════════════════════════════════════════════
    // PROP-SPECIFIC STATE
    // ════════════════════════════════════════════════════════════════════════
    
    /** Current disguise type (entity type registry name) */
    var disguiseType: String? = null
    
    /** Whether rotation is currently locked */
    var rotationLocked: Boolean = false
    
    /** Whether movement is frozen (mimicking static object) */
    var movementFrozen: Boolean = false
    
    /** Saved rotation when locked */
    var lockedYaw: Float = 0f
    var lockedPitch: Float = 0f
    
    /** Taunt cooldown remaining (ticks) */
    var tauntCooldown: Int = 0
    
    /** How long this prop has survived (ticks) */
    var survivalTicks: Int = 0
    
    /** Number of times this prop has been hit (but survived due to decoy) */
    var decoyHits: Int = 0
    
    // ════════════════════════════════════════════════════════════════════════
    // HUNTER-SPECIFIC STATE
    // ════════════════════════════════════════════════════════════════════════
    
    /** Number of props found this round */
    var propsFound: Int = 0
    
    /** Total false hits (hit non-prop entities) */
    var falseHits: Int = 0
    
    /** Scanner ability cooldown (ticks) */
    var scannerCooldown: Int = 0
    
    /** Tracker dart cooldown (ticks) */
    var trackerCooldown: Int = 0
    
    /** Stun grenade uses remaining */
    var stunGrenades: Int = 0
    
    /** Whether hunter is currently frozen (start of round) */
    var isFrozen: Boolean = false
    
    // ════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ════════════════════════════════════════════════════════════════════════
    
    /** Rounds won as either team */
    var roundsWon: Int = 0
    
    /** Total damage dealt this game */
    var damageDealt: Float = 0f
    
    /** Total damage taken this game */
    var damageTaken: Float = 0f
    
    // ════════════════════════════════════════════════════════════════════════
    // METHODS
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Resets round-specific state while preserving game statistics.
     */
    fun resetRoundState() {
        isAlive = true
        disguiseType = null
        rotationLocked = false
        movementFrozen = false
        tauntCooldown = 0
        survivalTicks = 0
        decoyHits = 0
        propsFound = 0
        falseHits = 0
        scannerCooldown = 0
        trackerCooldown = 0
        isFrozen = false
        // Note: team is NOT reset here - TeamBalancer handles that
    }
    
    /**
     * Restores the player to their original state before joining the game.
     */
    fun restoreState(player: ServerPlayer) {
        // Restore game mode
        player.setGameMode(originalGameMode)
        
        // Restore position
        // Center player in block to avoid suffocation or clipping
        player.teleportTo(
            originalPosition.x.toDouble() + 0.5, // Center X
            originalPosition.y.toDouble(),
            originalPosition.z.toDouble() + 0.5  // Center Z
        )
        
        // Restore inventory
        player.inventory.clearContent()
        for (i in originalInventory.indices) {
            player.inventory.setItem(i, originalInventory[i].copy())
        }
        
        // Clear any lingering effects
        player.removeAllEffects()
        player.isInvisible = false
        player.abilities.mayfly = false
        player.abilities.flying = false
        player.onUpdateAbilities()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropHuntPlayerData) return false
        return uuid == other.uuid
    }
    
    override fun hashCode(): Int = uuid.hashCode()
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                          GAME SETTINGS                                     ║
 * ║                                                                            ║
 * ║  Configurable settings for a Prop Hunt game. Can be customized per-arena   ║
 * ║  or per-game for different experiences.                                    ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
data class PropHuntSettings(
    // Player limits
    var minPlayers: Int = 4,
    var maxPlayers: Int = 16,
    
    // Round settings
    var maxRounds: Int = 5,
    var hideTimeSeconds: Int = 30,
    var roundTimeSeconds: Int = 180,
    var startCountdownSeconds: Int = 15,
    var roundEndDelaySeconds: Int = 10,
    
    // Team balance
    var hunterRatio: Float = 0.3f, // 30% hunters, 70% props
    var minHunters: Int = 1,
    var maxHunters: Int = 5,
    
    // Prop settings
    var propHealth: Float = 10f,
    var allowPropTaunts: Boolean = true,
    var tauntCooldownSeconds: Int = 20,
    var propSpeedMultiplier: Float = 1.0f,
    
    // Hunter settings
    var hunterDamage: Float = 4f,
    var falseHitPenaltyHealth: Float = 2f,
    var scannerCooldownSeconds: Int = 30,
    var trackerCooldownSeconds: Int = 45,
    var stunGrenadesPerRound: Int = 2,
    
    // Hint system
    var hunterHintsEnabled: Boolean = true,
    var hintIntervalSeconds: Int = 30,
    
    // Rewards
    var propSurviveRewardXP: Int = 50,
    var hunterFindRewardXP: Int = 30,
    var winningTeamRewardXP: Int = 100
) {
    fun copy(): PropHuntSettings = PropHuntSettings(
        minPlayers, maxPlayers, maxRounds, hideTimeSeconds, roundTimeSeconds,
        startCountdownSeconds, roundEndDelaySeconds, hunterRatio, minHunters,
        maxHunters, propHealth, allowPropTaunts, tauntCooldownSeconds,
        propSpeedMultiplier, hunterDamage, falseHitPenaltyHealth,
        scannerCooldownSeconds, trackerCooldownSeconds, stunGrenadesPerRound,
        hunterHintsEnabled, hintIntervalSeconds, propSurviveRewardXP,
        hunterFindRewardXP, winningTeamRewardXP
    )
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                        GLOBAL CONFIGURATION                                ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
class PropHuntConfig {
    /** Default settings used when creating new games */
    val defaultSettings = PropHuntSettings()
    
    /** Whether prop hunt is globally enabled */
    var enabled: Boolean = true
    
    /** Maximum concurrent games allowed */
    var maxConcurrentGames: Int = 5
    
    /** Whether to announce games server-wide */
    var announceGames: Boolean = true
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                         STATISTICS TRACKER                                 ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
class PropHuntStatistics {
    private val propWins = ConcurrentHashMap<UUID, Int>()
    private val hunterWins = ConcurrentHashMap<UUID, Int>()
    private val propsFound = ConcurrentHashMap<UUID, Int>()
    private val survivalTime = ConcurrentHashMap<UUID, Long>()
    
    fun recordPropWin(uuid: UUID) {
        propWins.merge(uuid, 1, Int::plus)
    }
    
    fun recordHunterWin(uuid: UUID) {
        hunterWins.merge(uuid, 1, Int::plus)
    }
    
    fun recordPropFound(hunterUuid: UUID) {
        propsFound.merge(hunterUuid, 1, Int::plus)
    }
    
    fun recordSurvivalTime(propUuid: UUID, ticks: Long) {
        survivalTime.merge(propUuid, ticks) { old, new -> maxOf(old, new) }
    }
    
    fun getPropWins(uuid: UUID): Int = propWins[uuid] ?: 0
    fun getHunterWins(uuid: UUID): Int = hunterWins[uuid] ?: 0
    fun getPropsFound(uuid: UUID): Int = propsFound[uuid] ?: 0
    fun getBestSurvivalTime(uuid: UUID): Long = survivalTime[uuid] ?: 0
}
