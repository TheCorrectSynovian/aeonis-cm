package com.qc.aeonis.minigame.manhunt

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                         MANHUNT GAME INSTANCE                              ║
 * ║                                                                            ║
 * ║  Represents a single Manhunt game session. Tracks all game-specific        ║
 * ║  state including players, hunter, difficulty, and timing information.      ║
 * ║                                                                            ║
 * ║  Inspired by Dream's Minecraft Manhunt series!                             ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
data class ManhuntGame(
    val id: String,
    val level: ServerLevel,
    val settings: ManhuntSettings,
    val creatorUUID: UUID
) {
    // ════════════════════════════════════════════════════════════════════════
    // GAME STATE
    // ════════════════════════════════════════════════════════════════════════
    
    /** Current game phase */
    var state: ManhuntGameState = ManhuntGameState.SETUP
    
    /** The hunter entity ID */
    var hunterEntityId: Int? = null
    
    /** Game start time (world time) */
    var startTime: Long = 0
    
    /** Time when hunter is released */
    var hunterReleaseTime: Long = 0
    
    // ════════════════════════════════════════════════════════════════════════
    // TIMING
    // ════════════════════════════════════════════════════════════════════════
    
    /** Ticks remaining in countdown phase */
    var countdownTicks: Int = 0
    
    /** Ticks remaining before hunter is released (head start) */
    var headStartTicks: Int = 0
    
    /** Total ticks elapsed in game */
    var elapsedTicks: Int = 0
    
    // ════════════════════════════════════════════════════════════════════════
    // PLAYER MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /** All players in this game, keyed by UUID */
    val players = ConcurrentHashMap<UUID, ManhuntPlayerData>()
    
    /** Spawn position for the game */
    var spawnPos: BlockPos? = null

    /** Hunter respawn anchor (spawn or discovered bed) */
    var hunterRespawnPos: BlockPos? = null
    
    /**
     * Adds a player to the game as a speedrunner.
     */
    fun addSpeedrunner(player: ServerPlayer) {
        val data = ManhuntPlayerData(
            uuid = player.uuid,
            name = player.name.string,
            role = ManhuntRole.SPEEDRUNNER,
            originalGameMode = player.gameMode.gameModeForPlayer,
            originalPosition = player.blockPosition(),
            originalInventory = saveInventory(player),
            isAlive = true
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
     * Gets all alive speedrunners' UUIDs.
     */
    fun getAliveSpeedrunners(): List<UUID> {
        return players.values
            .filter { it.role == ManhuntRole.SPEEDRUNNER && it.isAlive }
            .map { it.uuid }
    }
    
    /**
     * Gets all speedrunner player data.
     */
    fun getSpeedrunners(): List<ManhuntPlayerData> {
        return players.values.filter { it.role == ManhuntRole.SPEEDRUNNER }
    }
    
    /**
     * Gets all spectator player data.
     */
    fun getSpectators(): List<ManhuntPlayerData> {
        return players.values.filter { it.role == ManhuntRole.SPECTATOR || !it.isAlive }
    }
    
    /**
     * Marks a speedrunner as dead (eliminated).
     */
    fun eliminatePlayer(uuid: UUID) {
        players[uuid]?.isAlive = false
    }
    
    /**
     * Check if all speedrunners are eliminated.
     */
    fun areAllSpeedrunnersEliminated(): Boolean {
        return getAliveSpeedrunners().isEmpty()
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
     * Sends a message only to speedrunners.
     */
    fun broadcastToSpeedrunners(message: String) {
        val component = Component.literal(message)
        for (data in getSpeedrunners()) {
            val player = level.server?.playerList?.getPlayer(data.uuid)
            player?.sendSystemMessage(component)
        }
    }
    
    /**
     * Sends a title to all players in the game.
     */
    fun broadcastTitle(title: String, subtitle: String = "", fadeIn: Int = 10, stay: Int = 70, fadeOut: Int = 20) {
        for (playerData in players.values) {
            val player = level.server?.playerList?.getPlayer(playerData.uuid) ?: continue
            player.connection.send(
                net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(title))
            )
            if (subtitle.isNotEmpty()) {
                player.connection.send(
                    net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(subtitle))
                )
            }
            player.connection.send(
                net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut)
            )
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════
    
    private fun saveInventory(player: ServerPlayer): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        for (i in 0 until player.inventory.containerSize) {
            items.add(player.inventory.getItem(i).copy())
        }
        return items
    }
    
    /**
     * Gets formatted elapsed time string.
     */
    fun getElapsedTimeFormatted(): String {
        val totalSeconds = elapsedTicks / 20
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * Gets difficulty display name.
     */
    fun getDifficultyName(): String {
        return when {
            settings.difficulty <= 0.5f -> "§aEasy"
            settings.difficulty <= 1.0f -> "§eNormal"
            settings.difficulty <= 1.5f -> "§cHard"
            else -> "§4Nightmare"
        }
    }
}

/**
 * Game state phases
 */
enum class ManhuntGameState {
    SETUP,          // Configuring game, waiting for players
    COUNTDOWN,      // Game about to start
    HEAD_START,     // Speedrunners have head start (hunter frozen)
    ACTIVE,         // Hunter is hunting
    ENDED           // Game over
}

/**
 * Player roles
 */
enum class ManhuntRole {
    SPEEDRUNNER,    // Being hunted, trying to beat the game
    SPECTATOR       // Watching the game
}

/**
 * Per-player data for manhunt game
 */
data class ManhuntPlayerData(
    val uuid: UUID,
    val name: String,
    var role: ManhuntRole,
    val originalGameMode: GameType,
    val originalPosition: BlockPos,
    val originalInventory: List<ItemStack>,
    var isAlive: Boolean = true,
    var deathCount: Int = 0,
    var lastCompassUpdate: Long = 0
) {
    /**
     * Restores player to their original state after game ends.
     */
    fun restoreState(player: ServerPlayer) {
        // Restore gamemode
        player.setGameMode(originalGameMode)
        
        // Restore position
        player.teleportTo(
            originalPosition.x + 0.5,
            originalPosition.y.toDouble(),
            originalPosition.z + 0.5
        )
        
        // Clear current inventory
        player.inventory.clearContent()
        
        // Restore original inventory
        for ((index, item) in originalInventory.withIndex()) {
            if (index < player.inventory.containerSize) {
                player.inventory.setItem(index, item.copy())
            }
        }
    }
}

/**
 * Game settings configured via GUI
 */
data class ManhuntSettings(
    /** Difficulty multiplier (0.5 = easy, 1.0 = normal, 1.5 = hard, 2.0 = nightmare) */
    var difficulty: Float = 1.0f,
    
    /** Head start time in seconds before hunter is released */
    var headStartSeconds: Int = 30,
    
    /** Starting blocks for hunter */
    var hunterStartBlocks: Int = 64,
    
    /** Whether speedrunners respawn or are eliminated on death */
    var respawnEnabled: Boolean = false,
    
    /** Max respawns per speedrunner (-1 = unlimited) */
    var maxRespawns: Int = 3,
    
    /** Whether to give speedrunners tracking compass */
    var compassEnabled: Boolean = true,
    
    /** Compass update interval in ticks */
    var compassUpdateInterval: Int = 100,
    
    /** Whether nether is allowed */
    var netherEnabled: Boolean = true,
    
    /** Whether end is allowed */
    var endEnabled: Boolean = true,
    
    /** Win condition */
    var winCondition: WinCondition = WinCondition.KILL_DRAGON
) {
    companion object {
        fun easy() = ManhuntSettings(
            difficulty = 0.5f,
            headStartSeconds = 60,
            hunterStartBlocks = 32,
            respawnEnabled = true,
            maxRespawns = 5
        )
        
        fun normal() = ManhuntSettings(
            difficulty = 1.0f,
            headStartSeconds = 30,
            hunterStartBlocks = 64,
            respawnEnabled = true,
            maxRespawns = 3
        )
        
        fun hard() = ManhuntSettings(
            difficulty = 1.5f,
            headStartSeconds = 20,
            hunterStartBlocks = 96,
            respawnEnabled = true,
            maxRespawns = 1
        )
        
        fun nightmare() = ManhuntSettings(
            difficulty = 2.0f,
            headStartSeconds = 10,
            hunterStartBlocks = 128,
            respawnEnabled = false,
            maxRespawns = 0
        )
    }
}

/**
 * Win conditions for the game
 */
enum class WinCondition(val displayName: String) {
    KILL_DRAGON("Kill the Ender Dragon"),
    SURVIVE_TIME("Survive for set time"),
    REACH_END("Reach The End"),
    KILL_WITHER("Kill the Wither")
}
