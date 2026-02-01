package com.qc.aeonis.minigame.prophunt

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                         ARENA MANAGER                                      ║
 * ║                                                                            ║
 * ║  Handles arena creation, management, and teleportation including:          ║
 * ║  - Arena definition with boundaries and spawn points                       ║
 * ║  - Safe random teleportation within arena bounds                           ║
 * ║  - Chunk preloading for smooth gameplay                                    ║
 * ║  - Arena type presets (forest, village, castle, etc.)                      ║
 * ║  - Border enforcement                                                      ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object ArenaManager {
    private val logger = LoggerFactory.getLogger("aeonis-prophunt-arena")
    
    // ════════════════════════════════════════════════════════════════════════
    // STATE TRACKING
    // ════════════════════════════════════════════════════════════════════════
    
    /** All registered arenas indexed by ID */
    private val arenas = ConcurrentHashMap<String, PropHuntArena>()
    
    /** Default arena configurations for different types */
    private val arenaPresets = mapOf(
        "small" to ArenaPreset(radius = 30, minY = -10, maxY = 50),
        "medium" to ArenaPreset(radius = 50, minY = -20, maxY = 80),
        "large" to ArenaPreset(radius = 80, minY = -30, maxY = 100),
        "village" to ArenaPreset(radius = 60, minY = -10, maxY = 60, preferBiomes = listOf("plains", "savanna")),
        "forest" to ArenaPreset(radius = 70, minY = -10, maxY = 80, preferBiomes = listOf("forest", "dark_forest")),
        "cave" to ArenaPreset(radius = 40, minY = -60, maxY = 0, underground = true),
        "nether" to ArenaPreset(radius = 50, minY = 30, maxY = 100, dimension = "nether")
    )
    
    // ════════════════════════════════════════════════════════════════════════
    // ARENA CREATION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets an existing arena or creates a new one at the specified location.
     */
    fun getOrCreateArena(id: String, level: ServerLevel, center: BlockPos): PropHuntArena? {
        // Return existing arena if found
        arenas[id]?.let { return it }
        
        // Create new arena
        val preset = arenaPresets["medium"] ?: return null
        val arena = createArena(id, level, center, preset)
        arenas[id] = arena
        
        logger.info("§a[PropHunt] §7Created arena '$id' at ${center.x}, ${center.y}, ${center.z}")
        return arena
    }
    
    /**
     * Creates a new arena with specified parameters.
     */
    fun createArena(id: String, level: ServerLevel, center: BlockPos, preset: ArenaPreset): PropHuntArena {
        // Calculate bounds
        val minPos = BlockPos(
            center.x - preset.radius,
            (center.y + preset.minY).coerceAtLeast(level.minY),
            center.z - preset.radius
        )
        val maxPos = BlockPos(
            center.x + preset.radius,
            (center.y + preset.maxY).coerceAtMost(level.maxY),
            center.z + preset.radius
        )
        
        // Create spawn points
        val propSpawns = generateSpawnPoints(level, center, preset.radius, 15, true)
        val hunterSpawns = generateSpawnPoints(level, center, preset.radius / 3, 5, false)
        
        // Determine lobby position (center, elevated)
        val lobbyY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.x, center.z) + 1
        val lobbyPos = BlockPos(center.x, lobbyY, center.z)
        
        return PropHuntArena(
            id = id,
            center = center,
            minBounds = minPos,
            maxBounds = maxPos,
            lobbySpawn = lobbyPos,
            propSpawns = propSpawns,
            hunterSpawns = hunterSpawns,
            preset = preset
        )
    }
    
    /**
     * Generates random spawn points within the arena.
     */
    private fun generateSpawnPoints(
        level: ServerLevel,
        center: BlockPos,
        radius: Int,
        count: Int,
        scattered: Boolean
    ): List<BlockPos> {
        val spawns = mutableListOf<BlockPos>()
        val random = Random()
        var attempts = 0
        val maxAttempts = count * 20
        
        while (spawns.size < count && attempts < maxAttempts) {
            attempts++
            
            val distance = if (scattered) {
                random.nextDouble() * radius
            } else {
                random.nextDouble() * (radius * 0.5)
            }
            
            val angle = random.nextDouble() * 2 * Math.PI
            val x = center.x + (cos(angle) * distance).toInt()
            val z = center.z + (sin(angle) * distance).toInt()
            
            // Find safe Y position
            val y = findSafeY(level, x, z)
            if (y != null) {
                val pos = BlockPos(x, y, z)
                
                // Ensure minimum distance from other spawns
                val minSpacing = if (scattered) 8 else 3
                if (spawns.none { it.closerThan(pos, minSpacing.toDouble()) }) {
                    spawns.add(pos)
                }
            }
        }
        
        // Fallback if not enough spawns generated
        while (spawns.size < count) {
            val fallbackY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.x, center.z)
            spawns.add(BlockPos(center.x, fallbackY, center.z))
        }
        
        return spawns
    }
    
    /**
     * Finds a safe Y coordinate for spawning.
     */
    private fun findSafeY(level: ServerLevel, x: Int, z: Int): Int? {
        val topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        
        // Check if position is valid (solid block below, air above)
        for (y in topY downTo level.minY + 5) {
            val below = level.getBlockState(BlockPos(x, y - 1, z))
            val at = level.getBlockState(BlockPos(x, y, z))
            val above = level.getBlockState(BlockPos(x, y + 1, z))
            
            if (below.isSolid && !at.isSolid && !above.isSolid) {
                // Additional safety checks
                val belowBlock = below.block
                if (belowBlock != Blocks.LAVA && belowBlock != Blocks.MAGMA_BLOCK && 
                    belowBlock != Blocks.CACTUS && belowBlock != Blocks.SWEET_BERRY_BUSH) {
                    return y
                }
            }
        }
        
        return null
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ARENA MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets an arena by ID.
     */
    fun getArena(id: String): PropHuntArena? = arenas[id]
    
    /**
     * Removes an arena.
     */
    fun removeArena(id: String): Boolean {
        return arenas.remove(id) != null
    }
    
    /**
     * Lists all arena IDs.
     */
    fun listArenas(): List<String> = arenas.keys.toList()
    
    /**
     * Sets a custom arena with manually defined boundaries.
     */
    fun setCustomArena(
        id: String,
        level: ServerLevel,
        pos1: BlockPos,
        pos2: BlockPos,
        lobbyPos: BlockPos
    ): PropHuntArena {
        val minX = minOf(pos1.x, pos2.x)
        val minY = minOf(pos1.y, pos2.y)
        val minZ = minOf(pos1.z, pos2.z)
        val maxX = maxOf(pos1.x, pos2.x)
        val maxY = maxOf(pos1.y, pos2.y)
        val maxZ = maxOf(pos1.z, pos2.z)
        
        val center = BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
        val radius = maxOf(maxX - minX, maxZ - minZ) / 2
        
        val propSpawns = generateSpawnPoints(level, center, radius, 15, true)
        val hunterSpawns = generateSpawnPoints(level, center, radius / 3, 5, false)
        
        val arena = PropHuntArena(
            id = id,
            center = center,
            minBounds = BlockPos(minX, minY, minZ),
            maxBounds = BlockPos(maxX, maxY, maxZ),
            lobbySpawn = lobbyPos,
            propSpawns = propSpawns,
            hunterSpawns = hunterSpawns,
            preset = ArenaPreset(radius = radius)
        )
        
        arenas[id] = arena
        return arena
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TELEPORTATION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Teleports a player to the arena lobby.
     */
    fun teleportToLobby(player: ServerPlayer, arena: PropHuntArena) {
        preloadChunksAround(player.level() as ServerLevel, arena.lobbySpawn, 3)
        
        // Center player in block to avoid suffocation or clipping
        player.teleportTo(
            arena.lobbySpawn.x + 0.5, // Center X
            arena.lobbySpawn.y.toDouble(),
            arena.lobbySpawn.z + 0.5  // Center Z
        )
        
        player.sendSystemMessage(Component.literal("§a[PropHunt] §7Teleported to arena lobby!"))
    }
    
    /**
     * Teleports a player to a random spawn point for their team.
     */
    fun teleportToRandomSpawn(player: ServerPlayer, arena: PropHuntArena, team: PropHuntTeam) {
        val spawns = when (team) {
            PropHuntTeam.PROP -> arena.propSpawns
            PropHuntTeam.HUNTER -> arena.hunterSpawns
            PropHuntTeam.NONE -> listOf(arena.lobbySpawn)
        }
        
        if (spawns.isEmpty()) {
            teleportToLobby(player, arena)
            return
        }
        
        val spawn = spawns.random()
        preloadChunksAround(player.level() as ServerLevel, spawn, 2)
        
        // Center player in block to avoid suffocation or clipping
        player.teleportTo(
            spawn.x + 0.5, // Center X
            spawn.y.toDouble(),
            spawn.z + 0.5  // Center Z
        )
        
        // Random facing direction for props (helps with hiding)
        if (team == PropHuntTeam.PROP) {
            player.yRot = (Math.random() * 360 - 180).toFloat()
        }
    }
    
    /**
     * Safe random teleport within arena bounds with validation.
     */
    fun safeRandomTeleport(player: ServerPlayer, arena: PropHuntArena): Boolean {
        val level = player.level() as ServerLevel
        val random = Random()
        var attempts = 0
        
        while (attempts < 50) {
            attempts++
            
            val x = random.nextInt(arena.maxBounds.x - arena.minBounds.x) + arena.minBounds.x
            val z = random.nextInt(arena.maxBounds.z - arena.minBounds.z) + arena.minBounds.z
            
            val y = findSafeY(level, x, z)
            if (y != null && y >= arena.minBounds.y && y <= arena.maxBounds.y) {
                preloadChunksAround(level, BlockPos(x, y, z), 2)
                // Center player in block to avoid suffocation or clipping
                player.teleportTo(x + 0.5, y.toDouble(), z + 0.5)
                return true
            }
        }
        
        // Fallback to lobby
        teleportToLobby(player, arena)
        return false
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // CHUNK MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Preloads chunks around a position to prevent lag during teleportation.
     */
    fun preloadChunksAround(level: ServerLevel, pos: BlockPos, radius: Int) {
        val centerChunk = ChunkPos(pos)
        
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val chunk = ChunkPos(centerChunk.x + dx, centerChunk.z + dz)
                level.getChunk(chunk.x, chunk.z)
            }
        }
    }
    
    /**
     * Preloads all chunks within an arena.
     */
    fun preloadArena(level: ServerLevel, arena: PropHuntArena) {
        val minChunk = ChunkPos(arena.minBounds)
        val maxChunk = ChunkPos(arena.maxBounds)
        
        var loaded = 0
        for (cx in minChunk.x..maxChunk.x) {
            for (cz in minChunk.z..maxChunk.z) {
                level.getChunk(cx, cz)
                loaded++
            }
        }
        
        logger.info("§a[PropHunt] §7Preloaded $loaded chunks for arena '${arena.id}'")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // BORDER ENFORCEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if a player is within arena bounds.
     */
    fun isWithinBounds(player: ServerPlayer, arena: PropHuntArena): Boolean {
        val pos = player.blockPosition()
        return pos.x >= arena.minBounds.x && pos.x <= arena.maxBounds.x &&
               pos.y >= arena.minBounds.y && pos.y <= arena.maxBounds.y &&
               pos.z >= arena.minBounds.z && pos.z <= arena.maxBounds.z
    }
    
    /**
     * Enforces arena borders by pushing players back.
     */
    fun enforceBorders(player: ServerPlayer, arena: PropHuntArena): Boolean {
        if (isWithinBounds(player, arena)) return false
        
        val pos = player.position()
        var newX = pos.x
        var newY = pos.y
        var newZ = pos.z
        val pushStrength = 0.5
        
        // Push player back toward center
        if (player.blockPosition().x < arena.minBounds.x) {
            newX = arena.minBounds.x + 1.0
        } else if (player.blockPosition().x > arena.maxBounds.x) {
            newX = arena.maxBounds.x - 1.0
        }
        
        if (player.blockPosition().y < arena.minBounds.y) {
            newY = arena.minBounds.y + 1.0
        } else if (player.blockPosition().y > arena.maxBounds.y) {
            newY = arena.maxBounds.y - 1.0
        }
        
        if (player.blockPosition().z < arena.minBounds.z) {
            newZ = arena.minBounds.z + 1.0
        } else if (player.blockPosition().z > arena.maxBounds.z) {
            newZ = arena.maxBounds.z - 1.0
        }
        
        player.teleportTo(newX, newY, newZ)
        player.sendSystemMessage(Component.literal("§c[PropHunt] §7You've reached the arena border!"))
        
        return true
    }
    
    /**
     * Gets the distance to the nearest border.
     */
    fun getDistanceToBorder(player: ServerPlayer, arena: PropHuntArena): Double {
        val pos = player.blockPosition()
        
        // Calculate distance to each border (X, Y, Z)
        val distX = minOf(pos.x - arena.minBounds.x, arena.maxBounds.x - pos.x).toDouble()
        val distY = minOf(pos.y - arena.minBounds.y, arena.maxBounds.y - pos.y).toDouble()
        val distZ = minOf(pos.z - arena.minBounds.z, arena.maxBounds.z - pos.z).toDouble()
        
        return minOf(distX, distY, distZ)
    }
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                           ARENA DATA CLASS                                 ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
data class PropHuntArena(
    val id: String,
    val center: BlockPos,
    val minBounds: BlockPos,
    val maxBounds: BlockPos,
    val lobbySpawn: BlockPos,
    val propSpawns: List<BlockPos>,
    val hunterSpawns: List<BlockPos>,
    val preset: ArenaPreset
) {
    /** Gets the arena's bounding box as an AABB */
    fun toAABB(): AABB = AABB(
        // Convert BlockPos bounds to AABB (inclusive)
        minBounds.x.toDouble(), minBounds.y.toDouble(), minBounds.z.toDouble(),
        maxBounds.x.toDouble(), maxBounds.y.toDouble(), maxBounds.z.toDouble()
    )
    
    /** Gets the radius of the arena */
    fun getRadius(): Int = maxOf(maxBounds.x - minBounds.x, maxBounds.z - minBounds.z) / 2
    
    /** Gets a string description of the arena */
    fun getDescription(): String {
        val sizeX = maxBounds.x - minBounds.x
        val sizeY = maxBounds.y - minBounds.y
        val sizeZ = maxBounds.z - minBounds.z
        return "§7Arena '§e$id§7': ${sizeX}x${sizeY}x${sizeZ} blocks"
    }
}

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                         ARENA PRESET CONFIG                                ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
data class ArenaPreset(
    val radius: Int = 50,
    val minY: Int = -20,
    val maxY: Int = 80,
    val preferBiomes: List<String> = emptyList(),
    val underground: Boolean = false,
    val dimension: String = "overworld"
)

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                        TEAM BALANCER                                       ║
 * ║                                                                            ║
 * ║  Handles dynamic team balancing based on player count and settings.        ║
 * ║  Ensures fair team distribution and rotates roles between rounds.          ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object TeamBalancer {
    
    /**
     * Balances teams for a new round.
     * Uses round-robin to ensure players experience both roles.
     */
    fun balanceTeams(game: PropHuntGame) {
        val settings = game.settings
        val players = game.players.values.toMutableList()
        
        if (players.isEmpty()) return
        
        // Shuffle players for randomness
        players.shuffle()
        
        // Calculate hunter count
        val hunterCount = calculateHunterCount(players.size, settings)
        
        // Prioritize players who were hunters last round to be props
        // (so everyone gets to experience both roles)
        val previousHunters = players.filter { it.team == PropHuntTeam.HUNTER }
        val previousProps = players.filter { it.team == PropHuntTeam.PROP || it.team == PropHuntTeam.NONE }
        
        // Reorder: previous hunters first (they become props), then others
        val orderedPlayers = (previousHunters + previousProps).toMutableList()
        if (orderedPlayers.size != players.size) {
            orderedPlayers.clear()
            orderedPlayers.addAll(players)
        }
        
        // Assign teams
        for ((index, playerData) in orderedPlayers.withIndex()) {
            if (index < orderedPlayers.size - hunterCount) {
                playerData.team = PropHuntTeam.PROP
            } else {
                playerData.team = PropHuntTeam.HUNTER
            }
            
            // Update scoreboard
            val player = game.level.server?.playerList?.getPlayer(playerData.uuid)
            if (player != null) {
                PropHuntManager.updatePlayerTeam(player, playerData.team)
            }
        }
        
        // Announce team assignments
        val propCount = orderedPlayers.count { it.team == PropHuntTeam.PROP }
        val actualHunterCount = orderedPlayers.count { it.team == PropHuntTeam.HUNTER }
        game.broadcast("§7Teams: §a$propCount Props §7vs §c$actualHunterCount Hunters")
    }
    
    /**
     * Calculates the optimal number of hunters.
     */
    private fun calculateHunterCount(playerCount: Int, settings: PropHuntSettings): Int {
        // Base calculation from ratio
        val baseCount = (playerCount * settings.hunterRatio).toInt()
        
        // Apply min/max constraints
        return baseCount
            .coerceAtLeast(settings.minHunters)
            .coerceAtMost(settings.maxHunters)
            .coerceAtMost(playerCount - 1) // Always at least 1 prop
    }
    
    /**
     * Force-assigns a player to a specific team.
     */
    fun assignToTeam(player: ServerPlayer, team: PropHuntTeam, game: PropHuntGame) {
        val playerData = game.players[player.uuid] ?: return
        playerData.team = team
        PropHuntManager.updatePlayerTeam(player, team)
        
        val teamName = when (team) {
            PropHuntTeam.PROP -> "§aProps"
            PropHuntTeam.HUNTER -> "§cHunters"
            PropHuntTeam.NONE -> "§7Spectators"
        }
        player.sendSystemMessage(Component.literal("§e[PropHunt] §7You are now on team: $teamName"))
    }
    
    /**
     * Gets the team with fewer players for balancing join requests.
     */
    fun getSmallerTeam(game: PropHuntGame): PropHuntTeam {
        val propCount = game.getProps().size
        val hunterCount = game.getHunters().size
        
        return if (propCount <= hunterCount) PropHuntTeam.PROP else PropHuntTeam.HUNTER
    }
}
