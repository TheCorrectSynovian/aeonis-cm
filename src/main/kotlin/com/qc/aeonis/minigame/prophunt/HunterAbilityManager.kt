package com.qc.aeonis.minigame.prophunt

import com.qc.aeonis.util.playNotifySound

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      HUNTER ABILITY MANAGER                                ║
 * ║                                                                            ║
 * ║  Manages all hunter-specific abilities and mechanics including:            ║
 * ║  - Tracking tools (scanner, compass, spectral arrow)                       ║
 * ║  - Cooldown-based abilities (dash, pulse)                                  ║
 * ║  - Limited-use gadgets (stun grenades, reveal flares)                      ║
 * ║  - Hunter hints system                                                     ║
 * ║  - False hit penalties                                                     ║
 * ║                                                                            ║
 * ║  Hunters have powerful tools but must use them wisely. False positives     ║
 * ║  (hitting non-prop entities) result in health penalties.                   ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object HunterAbilityManager {
    private val logger = LoggerFactory.getLogger("aeonis-prophunt-hunters")
    
    // ════════════════════════════════════════════════════════════════════════
    // STATE TRACKING
    // ════════════════════════════════════════════════════════════════════════
    
    /** Tracks scanner pulse active states */
    private val activeScanners = ConcurrentHashMap<UUID, Int>()
    
    /** Tracks tracker dart targets */
    private val trackedProps = ConcurrentHashMap<UUID, UUID>() // Hunter -> Tracked Prop
    
    /** Tracks stun grenade throws in progress */
    private val stunGrenadeProjectiles = ConcurrentHashMap<Int, UUID>() // Snowball ID -> Hunter UUID
    
    // ════════════════════════════════════════════════════════════════════════
    // HUNTER SETUP
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Sets up a player as a hunter with appropriate equipment and state.
     */
    fun setupHunter(player: ServerPlayer, game: PropHuntGame) {
        val playerData = game.players[player.uuid] ?: return
        
        // Set game mode
        player.setGameMode(GameType.ADVENTURE)
        
        // Clear and give hunter items
        player.inventory.clearContent()
        giveHunterItems(player, game.settings)
        
        // Initialize ability states
        playerData.scannerCooldown = 0
        playerData.trackerCooldown = 0
        playerData.stunGrenades = game.settings.stunGrenadesPerRound
        
        // Give hunter strength for combat
        player.addEffect(MobEffectInstance(MobEffects.STRENGTH, Int.MAX_VALUE, 0, false, false, false))
        
        player.sendSystemMessage(Component.literal("§c[PropHunt] §7You are a §c§lHUNTER§7! Find and eliminate all props!"))
    }
    
    /**
     * Gives hunter-specific items to the player.
     */
    private fun giveHunterItems(player: ServerPlayer, settings: PropHuntSettings) {
        // Slot 0: Hunter's Sword (main weapon)
        val sword = ItemStack(Items.IRON_SWORD)
        sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§c§lHunter's Blade"))
        player.inventory.setItem(0, sword)
        
        // Slot 1: Scanner (reveals nearby props)
        val scanner = ItemStack(Items.ECHO_SHARD)
        scanner.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§b§lProp Scanner §7(Right-Click)"))
        player.inventory.setItem(1, scanner)
        
        // Slot 2: Tracker Dart (marks a prop through walls)
        val tracker = ItemStack(Items.SPECTRAL_ARROW)
        tracker.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§e§lTracker Dart §7(Right-Click)"))
        tracker.count = 1
        player.inventory.setItem(2, tracker)
        
        // Slot 3: Stun Grenades
        val stunGrenades = ItemStack(Items.SNOWBALL)
        stunGrenades.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§d§lStun Grenade §7(Throw)"))
        stunGrenades.count = settings.stunGrenadesPerRound
        player.inventory.setItem(3, stunGrenades)
        
        // Slot 4: Compass (points to nearest prop when active)
        val compass = ItemStack(Items.COMPASS)
        compass.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§6§lProp Compass"))
        player.inventory.setItem(4, compass)
        
        // Slot 5: Info book
        val infoBook = ItemStack(Items.WRITTEN_BOOK)
        infoBook.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§e§lHunter's Guide"))
        player.inventory.setItem(5, infoBook)
        
        // Slot 8: Food for healing
        val food = ItemStack(Items.COOKED_BEEF)
        food.count = 16
        player.inventory.setItem(8, food)
    }
    
    /**
     * Resets hunter state at end of round.
     */
    fun resetHunter(player: ServerPlayer) {
        activeScanners.remove(player.uuid)
        trackedProps.remove(player.uuid)
        player.removeEffect(MobEffects.STRENGTH)
        player.removeEffect(MobEffects.BLINDNESS)
        player.removeEffect(MobEffects.SLOW_FALLING)
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // HUNTER FREEZE (Start of Round)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Freezes or unfreezes a hunter (used during hide phase).
     */
    fun freezeHunter(player: ServerPlayer, freeze: Boolean) {
        val game = PropHuntManager.getPlayerGame(player) ?: return
        val playerData = game.players[player.uuid] ?: return
        
        if (freeze) {
            playerData.isFrozen = true
            // Complete blindness and immobility
            player.addEffect(MobEffectInstance(MobEffects.BLINDNESS, Int.MAX_VALUE, 0, false, false, false))
            player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, Int.MAX_VALUE, 255, false, false, false))
            player.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, Int.MAX_VALUE, 128, false, false, false))
            
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7You are §cFROZEN§7 and §cBLINDED§7 while props hide!"))
        } else {
            playerData.isFrozen = false
            player.removeEffect(MobEffects.BLINDNESS)
            player.removeEffect(MobEffects.SLOWNESS)
            player.removeEffect(MobEffects.JUMP_BOOST)
            
            player.sendSystemMessage(Component.literal("§a[PropHunt] §7You are §aFREE§7! Hunt them down!"))
            player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 0.5f, 1.5f)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // SCANNER ABILITY
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Activates the prop scanner ability.
     * Sends out a pulse that briefly highlights nearby props.
     */
    fun useScanner(player: ServerPlayer, game: PropHuntGame): Boolean {
        val playerData = game.players[player.uuid] ?: return false
        val settings = game.settings
        
        // Check cooldown
        if (playerData.scannerCooldown > 0) {
            val secondsLeft = playerData.scannerCooldown / 20
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7Scanner on cooldown! §e${secondsLeft}s§7 remaining."))
            return false
        }
        
        // Set cooldown
        playerData.scannerCooldown = settings.scannerCooldownSeconds * 20
        
        // Perform scan
        val level = player.level() as? ServerLevel ?: return false
        val scanRadius = 15.0
        val scanBox = AABB.ofSize(player.position(), scanRadius * 2, scanRadius, scanRadius * 2)
        
        // Visual pulse effect
        spawnScanPulse(player, level, scanRadius)
        
        // Find props in range
        var propsFound = 0
        for (propData in game.getProps()) {
            if (!propData.isAlive) continue
            
            val propPlayer = level.server?.playerList?.getPlayer(propData.uuid) ?: continue
            val distance = player.position().distanceTo(propPlayer.position())
            
            if (distance <= scanRadius) {
                propsFound++
                
                // Give brief glowing effect to prop's disguise entity
                val disguise = PropDisguiseManager.getDisguiseEntity(propPlayer)
                if (disguise is LivingEntity) {
                    disguise.addEffect(MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false))
                }
                
                // Direction hint to hunter
                val direction = getDirectionString(player, propPlayer)
                player.sendSystemMessage(Component.literal("§b[Scanner] §7Prop detected §e$direction §7(~${distance.toInt()}m)"))
                
                // Alert the prop
                propPlayer.sendSystemMessage(Component.literal("§c[PropHunt] §7A hunter is scanning nearby! Stay still!"))
                propPlayer.playNotifySound(SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.3f, 2.0f)
            }
        }
        
        if (propsFound == 0) {
            player.sendSystemMessage(Component.literal("§b[Scanner] §7No props detected in range."))
        } else {
            player.sendSystemMessage(Component.literal("§b[Scanner] §7Detected §e$propsFound§7 prop(s) nearby!"))
        }
        
        player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 2.0f)
        return true
    }
    
    /**
     * Creates a visual pulse effect for the scanner.
     */
    private fun spawnScanPulse(player: ServerPlayer, level: ServerLevel, radius: Double) {
        val center = player.position()
        
        // Ring of particles expanding outward
        for (ring in 1..3) {
            val ringRadius = (radius / 3) * ring
            val particles = (ringRadius * 8).toInt()
            
            for (i in 0 until particles) {
                val angle = (2 * Math.PI / particles) * i
                val x = center.x + cos(angle) * ringRadius
                val z = center.z + sin(angle) * ringRadius
                
                level.sendParticles(
                    ParticleTypes.SONIC_BOOM,
                    x, center.y + 1, z,
                    1, 0.0, 0.0, 0.0, 0.0
                )
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TRACKER DART
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Uses the tracker dart ability.
     * Marks the nearest prop with a glowing effect visible through walls.
     */
    fun useTracker(player: ServerPlayer, game: PropHuntGame): Boolean {
        val playerData = game.players[player.uuid] ?: return false
        val settings = game.settings
        
        // Check cooldown
        if (playerData.trackerCooldown > 0) {
            val secondsLeft = playerData.trackerCooldown / 20
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7Tracker on cooldown! §e${secondsLeft}s§7 remaining."))
            return false
        }
        
        // Find nearest prop
        val level = player.level() as? ServerLevel ?: return false
        var nearestProp: ServerPlayer? = null
        var nearestDistance = Double.MAX_VALUE
        
        for (propData in game.getProps()) {
            if (!propData.isAlive) continue
            val propPlayer = level.server?.playerList?.getPlayer(propData.uuid) ?: continue
            val distance = player.position().distanceTo(propPlayer.position())
            
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestProp = propPlayer
            }
        }
        
        if (nearestProp == null) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7No props to track!"))
            return false
        }
        
        // Set cooldown
        playerData.trackerCooldown = settings.trackerCooldownSeconds * 20
        
        // Mark the prop
        trackedProps[player.uuid] = nearestProp.uuid
        
        // Apply glowing effect to disguise
        val disguise = PropDisguiseManager.getDisguiseEntity(nearestProp)
        if (disguise is LivingEntity) {
            disguise.addEffect(MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false, false)) // 10 seconds
        }
        
        // Effects
        player.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0f, 0.5f)
        player.sendSystemMessage(Component.literal("§e[Tracker] §7A prop has been marked! Look for the glow!"))
        
        // Alert the prop
        nearestProp.sendSystemMessage(Component.literal("§c§l⚠ [PropHunt] §cYou have been MARKED by a tracker! You're glowing!"))
        nearestProp.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.0f)
        
        return true
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // STUN GRENADE
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Called when a stun grenade (snowball) lands.
     * Creates an area stun effect.
     */
    fun onStunGrenadeLand(thrower: ServerPlayer, hitPos: Vec3, game: PropHuntGame) {
        val level = thrower.level() as? ServerLevel ?: return
        val stunRadius = 5.0
        
        // Visual effect
        level.sendParticles(
            ParticleTypes.EXPLOSION_EMITTER,
            hitPos.x, hitPos.y + 1, hitPos.z,
            1, 0.0, 0.0, 0.0, 0.0
        )
        level.sendParticles(
            ParticleTypes.END_ROD,
            hitPos.x, hitPos.y + 1, hitPos.z,
            50, stunRadius / 2, 1.0, stunRadius / 2, 0.1
        )
        
        // Sound
        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
            SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5f, 0.5f)
        
        // Stun nearby props
        var stunned = 0
        for (propData in game.getProps()) {
            if (!propData.isAlive) continue
            val propPlayer = level.server?.playerList?.getPlayer(propData.uuid) ?: continue
            val distance = hitPos.distanceTo(propPlayer.position())
            
            if (distance <= stunRadius) {
                stunned++
                
                // Apply stun effects
                propPlayer.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 60, 2, false, false, false))
                propPlayer.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false))
                
                // Force unfreeze (reveals them!)
                if (propData.movementFrozen) {
                    PropDisguiseManager.toggleFreeze(propPlayer, game)
                }
                
                propPlayer.sendSystemMessage(Component.literal("§d§l⚡ [PropHunt] §dYou've been STUNNED!"))
            }
        }
        
        if (stunned > 0) {
            thrower.sendSystemMessage(Component.literal("§d[Stun] §7Stunned §e$stunned§7 prop(s)!"))
            game.broadcast("§d💥 §7A stun grenade was deployed!")
        } else {
            thrower.sendSystemMessage(Component.literal("§d[Stun] §7No props caught in the blast."))
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // HINT SYSTEM
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Gives hunters a hint about prop locations.
     */
    fun giveHint(game: PropHuntGame) {
        val aliveProps = game.getProps().filter { it.isAlive }
        if (aliveProps.isEmpty()) return
        
        val level = game.level
        
        // Choose a random alive prop
        val targetProp = aliveProps.random()
        val propPlayer = level.server?.playerList?.getPlayer(targetProp.uuid) ?: return
        
        // Generate a vague hint
        val hints = listOf(
            "§e⚡ §7Hint: A prop is hiding near §e${getBiomeName(propPlayer)}§7!",
            "§e⚡ §7Hint: One prop hasn't moved in a while... suspicious!",
            "§e⚡ §7Hint: Check around §eY=${propPlayer.blockY}§7 level!",
            "§e⚡ §7Hint: A prop is approximately §e${getDistanceHint(game, propPlayer)}§7m from the nearest hunter!",
            "§e⚡ §7Hint: Look for a §e${targetProp.disguiseType?.substringAfterLast(":") ?: "mysterious creature"}§7...",
            "§e⚡ §7Hint: One prop is facing §e${getCardinalDirection(propPlayer.yRot)}§7!",
            "§e⚡ §7Hint: Is that §e${targetProp.disguiseType?.substringAfterLast(":")}§7 supposed to be there?"
        )
        
        game.broadcastToHunters(hints.random())
        
        // Give props a warning
        game.broadcastToProps("§c⚠ §7The hunters received a hint about prop locations!")
    }
    
    /**
     * Gets a readable biome name.
     */
    private fun getBiomeName(player: ServerPlayer): String {
        val biome = player.level().getBiome(player.blockPosition())
        return biome.unwrapKey().map { it.identifier().path.replace("_", " ") }.orElse("unknown area")
    }
    
    /**
     * Gets approximate distance hint.
     */
    private fun getDistanceHint(game: PropHuntGame, propPlayer: ServerPlayer): Int {
        var minDistance = Double.MAX_VALUE
        for (hunterData in game.getHunters()) {
            val hunter = game.level.server?.playerList?.getPlayer(hunterData.uuid) ?: continue
            val distance = hunter.position().distanceTo(propPlayer.position())
            if (distance < minDistance) minDistance = distance
        }
        // Round to nearest 5
        return ((minDistance / 5).toInt() * 5).coerceIn(5, 100)
    }
    
    /**
     * Gets cardinal direction from yaw.
     */
    private fun getCardinalDirection(yaw: Float): String {
        val normalized = ((yaw % 360) + 360) % 360
        return when {
            normalized < 45 || normalized >= 315 -> "South"
            normalized < 135 -> "West"
            normalized < 225 -> "North"
            else -> "East"
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TICK HANDLER
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Called every tick to update hunter state.
     */
    fun tickHunter(player: ServerPlayer, game: PropHuntGame) {
        val playerData = game.players[player.uuid] ?: return
        
        // Update cooldowns
        if (playerData.scannerCooldown > 0) playerData.scannerCooldown--
        if (playerData.trackerCooldown > 0) playerData.trackerCooldown--
        
        // Update compass to point toward nearest prop
        updateCompass(player, game)
        
        // Show actionbar status
        showHunterActionBar(player, playerData, game)
    }
    
    /**
     * Updates the compass to point toward the nearest prop.
     */
    private fun updateCompass(player: ServerPlayer, game: PropHuntGame) {
        val level = player.level() as? ServerLevel ?: return
        
        // Find nearest alive prop
        var nearestProp: ServerPlayer? = null
        var nearestDistance = Double.MAX_VALUE
        
        for (propData in game.getProps()) {
            if (!propData.isAlive) continue
            val propPlayer = level.server?.playerList?.getPlayer(propData.uuid) ?: continue
            val distance = player.position().distanceTo(propPlayer.position())
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestProp = propPlayer
            }
        }
        
        if (nearestProp != null) {
            // The compass naturally points to world spawn, but we can give direction hints
            // Via actionbar since compass lodestone tracking requires block placement
            // This is handled in the actionbar display
        }
    }
    
    /**
     * Shows actionbar information to hunter players.
     */
    private fun showHunterActionBar(player: ServerPlayer, data: PropHuntPlayerData, game: PropHuntGame) {
        val statusParts = mutableListOf<String>()
        
        // Props remaining
        val propsAlive = game.getProps().count { it.isAlive }
        statusParts.add("§7Props: §c$propsAlive")
        
        // Scanner cooldown
        if (data.scannerCooldown > 0) {
            val seconds = data.scannerCooldown / 20
            statusParts.add("§b📡 ${seconds}s")
        } else {
            statusParts.add("§a📡 Ready")
        }
        
        // Tracker cooldown
        if (data.trackerCooldown > 0) {
            val seconds = data.trackerCooldown / 20
            statusParts.add("§e🎯 ${seconds}s")
        } else {
            statusParts.add("§a🎯 Ready")
        }
        
        // Stun grenades
        statusParts.add("§d💣 ${data.stunGrenades}")
        
        // Nearest prop direction (vague)
        val nearestProp = findNearestProp(player, game)
        if (nearestProp != null) {
            val direction = getDirectionString(player, nearestProp)
            val distance = player.position().distanceTo(nearestProp.position()).toInt()
            val distanceColor = when {
                distance < 10 -> "§c" // Very close - red
                distance < 25 -> "§e" // Medium - yellow
                else -> "§7" // Far - gray
            }
            statusParts.add("$distanceColor↗ $direction")
        }
        
        val message = statusParts.joinToString(" §8| ")
        player.displayClientMessage(Component.literal(message), true)
    }
    
    /**
     * Finds the nearest alive prop to a hunter.
     */
    private fun findNearestProp(hunter: ServerPlayer, game: PropHuntGame): ServerPlayer? {
        val level = hunter.level() as? ServerLevel ?: return null
        var nearest: ServerPlayer? = null
        var nearestDist = Double.MAX_VALUE
        
        for (propData in game.getProps()) {
            if (!propData.isAlive) continue
            val prop = level.server?.playerList?.getPlayer(propData.uuid) ?: continue
            val dist = hunter.position().distanceTo(prop.position())
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = prop
            }
        }
        return nearest
    }
    
    /**
     * Gets a direction string from one player to another.
     */
    private fun getDirectionString(from: ServerPlayer, to: ServerPlayer): String {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val angle = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
        val relative = ((angle - from.yRot + 360 + 180) % 360) - 180
        
        return when {
            relative > 135 || relative < -135 -> "Behind"
            relative > 45 -> "Right"
            relative < -45 -> "Left"
            else -> "Ahead"
        }
    }
    
    /**
     * Shows control tips to a hunter player.
     */
    fun showHunterTips(player: ServerPlayer) {
        player.sendSystemMessage(Component.literal(""))
        player.sendSystemMessage(Component.literal("§c§l═══════ HUNTER CONTROLS ═══════"))
        player.sendSystemMessage(Component.literal("§7• §eSword§7 → Attack suspicious entities"))
        player.sendSystemMessage(Component.literal("§7• §eEcho Shard§7 → Scanner (reveals nearby props)"))
        player.sendSystemMessage(Component.literal("§7• §eSpectral Arrow§7 → Tracker (marks nearest prop)"))
        player.sendSystemMessage(Component.literal("§7• §eSnowball§7 → Stun Grenade (freezes props)"))
        player.sendSystemMessage(Component.literal("§7• §eCompass§7 → Points toward props"))
        player.sendSystemMessage(Component.literal("§c§l⚠ §7Hitting non-props damages YOU!"))
        player.sendSystemMessage(Component.literal("§c§l════════════════════════════════"))
        player.sendSystemMessage(Component.literal(""))
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // HIT PROCESSING
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Processes a hunter's attack on an entity.
     * Returns true if it was a valid prop hit.
     */
    fun processHunterAttack(hunter: ServerPlayer, target: Entity, game: PropHuntGame): Boolean {
        // Check if target is a disguise entity
        val propUuid = PropDisguiseManager.isPropDisguise(target)
        
        if (propUuid != null) {
            // It's a prop disguise!
            val propPlayer = game.level.server?.playerList?.getPlayer(propUuid)
            if (propPlayer != null) {
                PropDisguiseManager.onPropCaught(propPlayer, hunter, game)
                return true
            }
        }
        
        // Check if it's a regular mob (false positive)
        if (target is LivingEntity && target !is ServerPlayer) {
            PropDisguiseManager.onFalseHit(hunter, target, game)
            return false
        }
        
        return false
    }
}
