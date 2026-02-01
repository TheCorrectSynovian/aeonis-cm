package com.qc.aeonis.entity

import com.qc.aeonis.config.AeonisFeatures
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Herobrine spawn cycles per player
 * 
 * Each player has their own Herobrine encounter cycle:
 * - Every 5-10 minutes, there's a chance Herobrine appears
 * - He can appear in different states (behind, roaming, staring, hunting)
 * - Up to 2 Herobrines can exist per player, but never visible together
 */
object HerobrineSpawner {
    
    // Track spawn timers per player (UUID -> ticks until next spawn check)
    private val playerSpawnTimers = ConcurrentHashMap<UUID, Int>()
    
    // Track active Herobrine entities per player (UUID -> List of Herobrine entity IDs)
    // Now supports up to 2 Herobrines per player
    private val activeHerobrines = ConcurrentHashMap<UUID, MutableList<Int>>()
    
    // Maximum Herobrines per player
    private const val MAX_HEROBRINES_PER_PLAYER = 2
    
    // Spawn interval: 8-12 minutes (9600-14400 ticks) - less frequent for more surprise/fear
    private const val MIN_SPAWN_INTERVAL = 9600   // 8 minutes
    private const val MAX_SPAWN_INTERVAL = 14400  // 12 minutes
    
    // Spawn chance when timer expires
    private const val SPAWN_CHANCE = 0.70 // 70% chance when timer expires - more unpredictable
    
    /**
     * Called every server tick to manage Herobrine spawns
     */
    fun tick(server: MinecraftServer) {
        // Check if extra mobs are enabled
        if (!AeonisFeatures.isExtraMobsEnabled(server)) {
            return
        }
        
        for (player in server.playerList.players) {
            tickPlayer(player)
            // Check if multiple Herobrines are in view and hide one
            checkAndHideVisibleHerobrines(player)
        }
        
        // Clean up entries for disconnected players
        val onlineUuids = server.playerList.players.map { it.uuid }.toSet()
        playerSpawnTimers.keys.removeIf { it !in onlineUuids }
        activeHerobrines.keys.removeIf { it !in onlineUuids }
    }
    
    private fun tickPlayer(player: ServerPlayer) {
        val level = player.level() as ServerLevel
        val uuid = player.uuid
        
        // Initialize timer if not present
        if (!playerSpawnTimers.containsKey(uuid)) {
            // Random initial delay (30 seconds to 3 minutes)
            playerSpawnTimers[uuid] = 600 + level.random.nextInt(3000)
        }
        
        // Clean up dead Herobrines from the list
        val herobrineList = activeHerobrines.getOrPut(uuid) { mutableListOf() }
        herobrineList.removeIf { id ->
            val entity = level.getEntity(id)
            entity == null || !entity.isAlive()
        }
        
        // Check if player already has max Herobrines
        if (herobrineList.size >= MAX_HEROBRINES_PER_PLAYER) {
            return // Don't spawn more
        }
        
        // Decrement timer
        val timer = playerSpawnTimers[uuid] ?: return
        if (timer > 0) {
            playerSpawnTimers[uuid] = timer - 1
            return
        }
        
        // Timer expired - check spawn conditions
        if (!canSpawnForPlayer(player)) {
            // Reset timer and try again later
            playerSpawnTimers[uuid] = getRandomSpawnInterval(level) / 2
            return
        }
        
        // Roll for spawn
        if (level.random.nextFloat() < SPAWN_CHANCE) {
            spawnHerobrineForPlayer(player)
        }
        
        // Reset timer regardless
        playerSpawnTimers[uuid] = getRandomSpawnInterval(level)
    }
    
    /**
     * Check if multiple Herobrines are visible to a player at once
     * If so, make one of them disappear (the one player is looking at less directly)
     */
    private fun checkAndHideVisibleHerobrines(player: ServerPlayer) {
        val level = player.level() as ServerLevel
        val herobrineList = activeHerobrines[player.uuid] ?: return
        
        if (herobrineList.size < 2) return // Need at least 2 to have a conflict
        
        // Get all visible Herobrines
        val visibleHerobrines = mutableListOf<Pair<HerobrineEntity, Double>>() // entity, dot product (how directly looking)
        
        for (id in herobrineList) {
            val entity = level.getEntity(id) as? HerobrineEntity ?: continue
            
            // Check if this Herobrine is in player's field of view
            val dotProduct = getViewDotProduct(player, entity)
            
            // If dot > 0.3, it's roughly in the player's forward 140° view arc
            if (dotProduct > 0.3 && player.hasLineOfSight(entity)) {
                visibleHerobrines.add(entity to dotProduct)
            }
        }
        
        // If 2 or more are visible, hide all but the one player is looking at most directly
        if (visibleHerobrines.size >= 2) {
            // Sort by dot product (highest = most directly looked at)
            visibleHerobrines.sortByDescending { it.second }
            
            // Keep the first one (most looked at), make others disappear
            for (i in 1 until visibleHerobrines.size) {
                val herobrine = visibleHerobrines[i].first
                herobrine.disappearWithEffect(level)
                herobrineList.remove(herobrine.id)
            }
        }
    }
    
    /**
     * Calculate dot product between player's look direction and direction to entity
     * Returns 1.0 if looking directly at entity, -1.0 if looking away
     */
    private fun getViewDotProduct(player: ServerPlayer, entity: HerobrineEntity): Double {
        val toEntity = Vec3(
            entity.x - player.x,
            entity.eyeY - player.eyeY,
            entity.z - player.z
        ).normalize()
        
        val lookVec = player.lookAngle.normalize()
        return lookVec.dot(toEntity)
    }
    
    private fun canSpawnForPlayer(player: ServerPlayer): Boolean {
        val level = player.level() as ServerLevel
        
        // Don't spawn in spectator mode
        if (player.isSpectator) return false
        
        // Check if player is in the overworld (Herobrine doesn't appear in other dimensions)
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false
        
        // Don't spawn if player is very close to spawn point (first 100 blocks)
        val playerDistFromOrigin = player.blockPosition().distSqr(net.minecraft.core.BlockPos.ZERO)
        if (playerDistFromOrigin < 10000) return false // Within 100 blocks of world origin
        
        // Herobrine spawns equally in day and night - he's always watching
        return true
    }
    
    private fun spawnHerobrineForPlayer(player: ServerPlayer) {
        val level = player.level() as ServerLevel
        
        // Choose a random state - heavily favor roaming and hunting for more action
        val state = when (level.random.nextInt(100)) {
            in 0..15 -> HerobrineEntity.HerobrineState.WATCHING_BEHIND   // 16% - classic behind spawn
            in 16..30 -> HerobrineEntity.HerobrineState.STARING         // 15% - staring from distance
            in 31..65 -> HerobrineEntity.HerobrineState.ROAMING         // 35% - roaming with sword (very visible)
            else -> HerobrineEntity.HerobrineState.HUNTING_ANIMALS       // 34% - hunting animals (kills cows!)
        }
        
        // If there's already a Herobrine, spawn the new one in the opposite direction
        val existingHerobrines = activeHerobrines[player.uuid]
        val avoidDirection: Vec3? = if (!existingHerobrines.isNullOrEmpty()) {
            val firstHerobrine = level.getEntity(existingHerobrines.first()) as? HerobrineEntity
            if (firstHerobrine != null) {
                // Direction from player to existing Herobrine - new one should spawn opposite
                Vec3(firstHerobrine.x - player.x, 0.0, firstHerobrine.z - player.z).normalize()
            } else null
        } else null
        
        val herobrine = HerobrineEntity.spawnBehindPlayer(level, player, state, avoidDirection)
        
        if (herobrine != null) {
            val herobrineList = activeHerobrines.getOrPut(player.uuid) { mutableListOf() }
            herobrineList.add(herobrine.id)
            
            // Debug logging (can be removed later)
            // println("[Aeonis] Herobrine spawned for ${player.name.string} in state: $state (total: ${herobrineList.size})")
        }
    }
    
    private fun getRandomSpawnInterval(level: ServerLevel): Int {
        return MIN_SPAWN_INTERVAL + level.random.nextInt(MAX_SPAWN_INTERVAL - MIN_SPAWN_INTERVAL)
    }
    
    /**
     * Force spawn a Herobrine for a player (for testing/commands)
     */
    fun forceSpawn(player: ServerPlayer, state: HerobrineEntity.HerobrineState): Boolean {
        val level = player.level() as ServerLevel
        
        // Remove all existing Herobrines for this player
        activeHerobrines[player.uuid]?.let { list ->
            for (id in list) {
                level.getEntity(id)?.discard()
            }
            list.clear()
        }
        
        val herobrine = HerobrineEntity.spawnBehindPlayer(level, player, state)
        
        if (herobrine != null) {
            val herobrineList = activeHerobrines.getOrPut(player.uuid) { mutableListOf() }
            herobrineList.add(herobrine.id)
            return true
        }
        return false
    }
    
    /**
     * Clear all Herobrines (for cleanup/reload)
     */
    fun clearAll(server: MinecraftServer) {
        for ((uuid, entityIds) in activeHerobrines) {
            for (entityId in entityIds) {
                for (level in server.allLevels) {
                    level.getEntity(entityId)?.discard()
                }
            }
        }
        activeHerobrines.clear()
        playerSpawnTimers.clear()
    }
}
