package com.qc.aeonis.minigame.prophunt

import com.qc.aeonis.network.AeonisNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.*
import net.minecraft.world.entity.animal.*
import net.minecraft.world.entity.animal.armadillo.Armadillo
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.*
import net.minecraft.world.entity.vehicle.Minecart
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      PROP DISGUISE MANAGER                                 ║
 * ║                                                                            ║
 * ║  Handles all prop disguise mechanics using the established /transform      ║
 * ║  system from AeonisCommands for reliable entity control.                   ║
 * ║                                                                            ║
 * ║  Features:                                                                 ║
 * ║  - Uses AeonisNetworking.setControlledEntity() for proper entity sync      ║
 * ║  - Rotation locking for inanimate objects                                  ║
 * ║  - Movement freezing to mimic static props                                 ║
 * ║  - Taunt abilities and particle effects                                    ║
 * ║  - Hit detection and reveal mechanics                                      ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropDisguiseManager {
    private val logger = LoggerFactory.getLogger("aeonis-prophunt-props")
    
    // ════════════════════════════════════════════════════════════════════════
    // STATE TRACKING
    // ════════════════════════════════════════════════════════════════════════
    
    /** Player UUID -> Disguise entity ID */
    private val disguiseEntities = ConcurrentHashMap<UUID, Int>()
    
    /** Player UUID -> Original game mode (for restore on undisguise) */
    private val originalGameModes = ConcurrentHashMap<UUID, GameType>()
    
    /** Player UUID -> Saved position when frozen */
    private val frozenPositions = ConcurrentHashMap<UUID, Vec3>()
    
    /** Player UUID -> Last movement time (for AFK detection) */
    private val lastMovementTime = ConcurrentHashMap<UUID, Long>()
    
    // ════════════════════════════════════════════════════════════════════════
    // DISGUISE CATEGORIES
    // Each category has different gameplay implications
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Small props - Harder to spot, but more fragile
     * Great for hiding in corners and small spaces
     */
    val SMALL_PROPS = listOf(
        EntityType.CHICKEN,
        EntityType.RABBIT,
        EntityType.BAT,
        EntityType.SILVERFISH,
        EntityType.ENDERMITE,
        EntityType.BEE,
        EntityType.FROG,
        EntityType.ALLAY,
        EntityType.PARROT,
        EntityType.CAT,
        EntityType.ARMADILLO
    )
    
    /**
     * Medium props - Balanced size, common in Minecraft worlds
     * Good for blending in with normal mobs
     */
    val MEDIUM_PROPS = listOf(
        EntityType.PIG,
        EntityType.SHEEP,
        EntityType.COW,
        EntityType.WOLF,
        EntityType.GOAT,
        EntityType.FOX,
        EntityType.OCELOT,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.CREEPER,
        EntityType.SPIDER,
        EntityType.VILLAGER,
        EntityType.WANDERING_TRADER
    )
    
    /**
     * Large props - Easy to spot, but intimidating
     * Risk/reward gameplay - hide in plain sight
     */
    val LARGE_PROPS = listOf(
        EntityType.HORSE,
        EntityType.DONKEY,
        EntityType.MULE,
        EntityType.LLAMA,
        EntityType.POLAR_BEAR,
        EntityType.IRON_GOLEM,
        EntityType.SNOW_GOLEM,
        EntityType.RAVAGER,
        EntityType.CAMEL
    )
    
    /**
     * Object props - Non-living entities that don't move
     * Perfect for rotation-lock strategy
     */
    val OBJECT_PROPS = listOf(
        EntityType.ARMOR_STAND,
        EntityType.MINECART
    )
    
    /**
     * Special props - Unique mechanics or rare disguises
     * Unlockable or earned through gameplay
     */
    val SPECIAL_PROPS = listOf(
        EntityType.AXOLOTL,
        EntityType.GLOW_SQUID,
        EntityType.DOLPHIN,
        EntityType.TURTLE,
        EntityType.PANDA,
        EntityType.SNIFFER,
        EntityType.WARDEN // The ultimate disguise - terrifying!
    )
    
    /**
     * Gets all available prop types for a game.
     */
    fun getAllPropTypes(settings: PropHuntSettings): List<EntityType<*>> {
        val props = mutableListOf<EntityType<*>>()
        props.addAll(SMALL_PROPS)
        props.addAll(MEDIUM_PROPS)
        props.addAll(LARGE_PROPS)
        props.addAll(OBJECT_PROPS)
        // Special props can be unlocked
        return props
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // DISGUISE ASSIGNMENT
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Assigns a random disguise to a prop player.
     * Uses weighted selection based on arena and settings.
     */
    fun assignRandomDisguise(player: ServerPlayer, game: PropHuntGame) {
        val availableProps = getAllPropTypes(game.settings)
        val propType = availableProps.random()
        applyDisguise(player, propType, game)
    }
    
    /**
     * Allows a prop to choose their disguise (if enabled in settings).
     */
    fun chooseDisguise(player: ServerPlayer, typeName: String, game: PropHuntGame): Boolean {
        // Find the entity type by name
        val propType = getAllPropTypes(game.settings).find { 
            EntityType.getKey(it).path.equals(typeName, ignoreCase = true)
        }
        
        if (propType == null) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7Unknown prop type: $typeName"))
            return false
        }
        
        applyDisguise(player, propType, game)
        return true
    }
    
    /**
     * Applies a disguise to a player using the established transform system.
     * Uses AeonisNetworking.setControlledEntity() for reliable entity control.
     */
    fun applyDisguise(player: ServerPlayer, propType: EntityType<*>, game: PropHuntGame) {
        val level = player.level() as? ServerLevel ?: return
        val playerData = game.players[player.uuid] ?: return
        
        // Remove any existing disguise first
        removeDisguise(player)
        
        // Store original gamemode
        originalGameModes[player.uuid] = player.gameMode.gameModeForPlayer
        
        // Clear inventory and give prop items
        player.inventory.clearContent()
        givePropItems(player, game.settings)
        
        // Spawn the disguise entity at player's location
        @Suppress("UNCHECKED_CAST")
        val disguise = (propType as EntityType<Entity>).spawn(
            level,
            player.blockPosition(),
            EntitySpawnReason.COMMAND
        )
        
        if (disguise == null) {
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7Failed to create disguise!"))
            return
        }
        
        // Configure the entity for prop hunt
        configureDisguiseEntity(disguise, player)
        
        // Track the disguise
        disguiseEntities[player.uuid] = disguise.id
        playerData.disguiseType = EntityType.getKey(propType).toString()
        
        // ═══════════════════════════════════════════════════════════════════
        // USE THE ESTABLISHED TRANSFORM SYSTEM
        // This is the same pattern as /transform command
        // ═══════════════════════════════════════════════════════════════════
        
        // Register entity for control via AeonisNetworking
        // This enables the proper position/rotation sync from AeonisManager tick
        AeonisNetworking.setControlledEntity(player, disguise.id)
        
        // Set player to survival mode (allows interactions) and make invisible
        // Player controls the entity through the networking system
        player.setGameMode(GameType.SURVIVAL)
        player.isInvisible = true
        
        // Sync health (optional for props - they usually have 1 hit)
        if (disguise is LivingEntity) {
            // Props keep their normal player health for balance
            // But we track the disguise health for hit detection
        }
        
        // Sync position
        player.teleportTo(disguise.x, disguise.y, disguise.z)
        
        // Announce to player
        val propName = EntityType.getKey(propType).path.replace("_", " ")
        player.sendSystemMessage(Component.literal("§a[PropHunt] §7You are disguised as a §e$propName§7!"))
        player.sendSystemMessage(Component.literal("§7Use §eWASD§7 to move. Your disguise follows you!"))
        
        // Play transformation sound
        player.playNotifySound(SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.0f)
        
        // Spawn particles
        spawnTransformParticles(player)
    }
    
    /**
     * Configures a disguise entity with proper settings.
     */
    private fun configureDisguiseEntity(entity: Entity, owner: ServerPlayer) {
        // Make the entity unable to be targeted by mobs
        if (entity is Mob) {
            entity.setNoAi(true)
            entity.isSilent = true
            entity.isNoGravity = false
        }
        
        // Make it invulnerable to everything except player attacks
        entity.isInvulnerable = false
        
        // Set custom name for identification (hidden)
        entity.customName = Component.literal("§8[PH:${owner.uuid}]")
        entity.isCustomNameVisible = false
        
        // Specific entity configurations
        when (entity) {
            is ArmorStand -> {
                entity.isInvisible = false
                entity.isNoGravity = false
                entity.setShowArms(true)
            }
            is Minecart -> {
                // Minecarts work naturally
            }
            is LivingEntity -> {
                // Remove AI behaviors
                entity.isNoGravity = false
            }
        }
    }
    
    /**
     * Gives prop-specific items to the player.
     */
    private fun givePropItems(player: ServerPlayer, settings: PropHuntSettings) {
        // Slot 0: Info item (shows controls)
        val infoItem = ItemStack(Items.NETHER_STAR)
        infoItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, 
            Component.literal("§b§lProp Controls"))
        // Add lore using custom data
        player.inventory.setItem(0, infoItem)
        
        // Slot 1: Rotation Lock toggle (if applicable)
        val lockItem = ItemStack(Items.COMPASS)
        lockItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§e§lLock Rotation §7(Right-Click)"))
        player.inventory.setItem(1, lockItem)
        
        // Slot 2: Freeze toggle
        val freezeItem = ItemStack(Items.BLUE_ICE)
        freezeItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§b§lFreeze §7(Right-Click)"))
        player.inventory.setItem(2, freezeItem)
        
        // Slot 3: Taunt (if enabled)
        if (settings.allowPropTaunts) {
            val tauntItem = ItemStack(Items.GOAT_HORN)
            tauntItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§d§lTaunt §7(Right-Click)"))
            player.inventory.setItem(3, tauntItem)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // DISGUISE REMOVAL
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Removes a player's disguise using the transform system.
     */
    fun removeDisguise(player: ServerPlayer) {
        val entityId = disguiseEntities.remove(player.uuid) ?: return
        val level = player.level() as? ServerLevel ?: return
        
        // Stop controlling via networking system
        AeonisNetworking.removeControlledEntity(player)
        
        // Find and remove the entity
        val entity = level.getEntity(entityId)
        entity?.discard()
        
        // Clear frozen state
        frozenPositions.remove(player.uuid)
        lastMovementTime.remove(player.uuid)
    }
    
    /**
     * Fully undisguises a player and restores their state.
     * Uses the same pattern as /untransform command.
     */
    fun undisguise(player: ServerPlayer) {
        removeDisguise(player)
        
        // Restore visibility
        player.isInvisible = false
        
        // Restore original gamemode
        val originalMode = originalGameModes.remove(player.uuid) ?: GameType.SURVIVAL
        player.setGameMode(originalMode)
        
        // Reset health to default
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue = 20.0
        player.health = 20.0f
        
        // Clear any freeze effects
        player.removeEffect(MobEffects.SLOW_FALLING)
        player.removeEffect(MobEffects.SLOWNESS)
        player.removeEffect(MobEffects.JUMP_BOOST)
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // PROP MECHANICS - Rotation Lock & Freeze
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Toggles rotation lock for a prop.
     * When locked, the disguise entity maintains its current rotation.
     */
    fun toggleRotationLock(player: ServerPlayer, game: PropHuntGame) {
        val playerData = game.players[player.uuid] ?: return
        
        playerData.rotationLocked = !playerData.rotationLocked
        
        if (playerData.rotationLocked) {
            playerData.lockedYaw = player.yRot
            playerData.lockedPitch = player.xRot
            player.sendSystemMessage(Component.literal("§a[PropHunt] §7Rotation §eLOCKED§7! Your disguise won't rotate."))
            player.playNotifySound(SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.5f, 1.5f)
        } else {
            player.sendSystemMessage(Component.literal("§a[PropHunt] §7Rotation §eUNLOCKED§7."))
            player.playNotifySound(SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.5f, 1.5f)
        }
    }
    
    /**
     * Toggles movement freeze for a prop.
     * When frozen, the prop cannot move - perfect for mimicking static objects.
     */
    fun toggleFreeze(player: ServerPlayer, game: PropHuntGame) {
        val playerData = game.players[player.uuid] ?: return
        
        playerData.movementFrozen = !playerData.movementFrozen
        
        if (playerData.movementFrozen) {
            // Save current position
            frozenPositions[player.uuid] = player.position()
            
            // Apply slowness to prevent movement
            player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, Int.MAX_VALUE, 255, false, false, false))
            player.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, Int.MAX_VALUE, 128, false, false, false)) // Negative jump
            
            player.sendSystemMessage(Component.literal("§b[PropHunt] §7Movement §cFROZEN§7! You cannot move."))
            player.playNotifySound(SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.5f, 1.0f)
            
            // Spawn freeze particles
            spawnFreezeParticles(player)
        } else {
            // Remove slowness
            player.removeEffect(MobEffects.SLOWNESS)
            player.removeEffect(MobEffects.JUMP_BOOST)
            frozenPositions.remove(player.uuid)
            
            player.sendSystemMessage(Component.literal("§b[PropHunt] §7Movement §aUNFROZEN§7."))
            player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5f, 1.2f)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TAUNT SYSTEM
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Performs a taunt action for a prop.
     * Taunts draw attention but can provide rewards or confuse hunters.
     */
    fun performTaunt(player: ServerPlayer, game: PropHuntGame) {
        val playerData = game.players[player.uuid] ?: return
        val settings = game.settings
        
        // Check cooldown
        if (playerData.tauntCooldown > 0) {
            val secondsLeft = playerData.tauntCooldown / 20
            player.sendSystemMessage(Component.literal("§c[PropHunt] §7Taunt on cooldown! §e${secondsLeft}s§7 remaining."))
            return
        }
        
        // Set cooldown
        playerData.tauntCooldown = settings.tauntCooldownSeconds * 20
        
        // Choose a random taunt
        val tauntType = TauntType.entries.random()
        executeTaunt(player, game, tauntType)
    }
    
    /**
     * Executes a specific taunt type.
     */
    private fun executeTaunt(player: ServerPlayer, game: PropHuntGame, taunt: TauntType) {
        val level = player.level() as? ServerLevel ?: return
        
        when (taunt) {
            TauntType.SOUND_DECOY -> {
                // Play a random mob sound at a random nearby location
                val offset = Vec3(
                    (Math.random() - 0.5) * 20,
                    (Math.random() - 0.5) * 5,
                    (Math.random() - 0.5) * 20
                )
                val soundPos = player.position().add(offset)
                
                val sounds = listOf(
                    SoundEvents.ZOMBIE_AMBIENT,
                    SoundEvents.SKELETON_AMBIENT,
                    SoundEvents.CREEPER_PRIMED,
                    SoundEvents.SHEEP_AMBIENT,
                    SoundEvents.COW_AMBIENT
                )
                
                level.playSound(null, soundPos.x, soundPos.y, soundPos.z, 
                    sounds.random(), SoundSource.HOSTILE, 1.0f, 1.0f)
                
                player.sendSystemMessage(Component.literal("§d[PropHunt] §7Sound decoy deployed!"))
                game.broadcastToHunters("§e⚠ §7A suspicious sound was heard nearby...")
            }
            
            TauntType.PARTICLE_BURST -> {
                // Create particles around a random area
                val offset = Vec3(
                    (Math.random() - 0.5) * 15,
                    Math.random() * 3,
                    (Math.random() - 0.5) * 15
                )
                val particlePos = player.position().add(offset)
                
                level.sendParticles(
                    ParticleTypes.FLAME,
                    particlePos.x, particlePos.y, particlePos.z,
                    30, 1.0, 1.0, 1.0, 0.05
                )
                
                player.sendSystemMessage(Component.literal("§d[PropHunt] §7Particle decoy activated!"))
                game.broadcastToHunters("§e⚠ §7Strange particles spotted somewhere...")
            }
            
            TauntType.FAKE_FOOTSTEPS -> {
                // Play footstep sounds in a line away from the player
                for (i in 1..5) {
                    val direction = Vec3(Math.random() - 0.5, 0.0, Math.random() - 0.5).normalize()
                    val stepPos = player.position().add(direction.scale(i * 2.0))
                    
                    level.playSound(null, stepPos.x, stepPos.y, stepPos.z,
                        SoundEvents.GRASS_STEP, SoundSource.PLAYERS, 0.5f, 1.0f)
                }
                
                player.sendSystemMessage(Component.literal("§d[PropHunt] §7Fake footsteps created!"))
            }
            
            TauntType.LAUGHING -> {
                // Risky taunt - plays at player location but gives bonus if not caught
                level.playSound(null, player.x, player.y, player.z,
                    SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 1.0f, 1.2f)
                
                player.sendSystemMessage(Component.literal("§d[PropHunt] §7You laughed! Risky..."))
                game.broadcastToHunters("§c§l⚠ §4A prop is taunting you!")
            }
            
            TauntType.FAKE_DEATH -> {
                // Creates death particles and sound
                level.sendParticles(
                    ParticleTypes.DAMAGE_INDICATOR,
                    player.x + (Math.random() - 0.5) * 10,
                    player.y + 1,
                    player.z + (Math.random() - 0.5) * 10,
                    20, 0.5, 0.5, 0.5, 0.1
                )
                
                level.playSound(null, player.x, player.y, player.z,
                    SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f)
                
                player.sendSystemMessage(Component.literal("§d[PropHunt] §7Fake death performed!"))
                game.broadcastToHunters("§a✔ §7A prop was eliminated... or was it?")
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TICK HANDLER
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Called every tick to update prop state.
     * NOTE: Entity position sync is handled by AeonisManager tick via setControlledEntity.
     * This tick only handles prop-specific mechanics like freeze, rotation lock, etc.
     */
    fun tickProp(player: ServerPlayer, game: PropHuntGame) {
        val playerData = game.players[player.uuid] ?: return
        val level = player.level() as? ServerLevel ?: return
        
        // Update cooldowns
        if (playerData.tauntCooldown > 0) {
            playerData.tauntCooldown--
        }
        
        // Track survival time
        if (playerData.isAlive) {
            playerData.survivalTicks++
        }
        
        // Get the disguise entity (controlled via AeonisNetworking)
        val entityId = disguiseEntities[player.uuid]
        if (entityId != null) {
            val disguise = level.getEntity(entityId)
            if (disguise != null) {
                // Handle rotation lock - override the normal rotation sync
                if (playerData.rotationLocked) {
                    disguise.yRot = playerData.lockedYaw
                    disguise.xRot = playerData.lockedPitch
                    if (disguise is LivingEntity) {
                        disguise.yHeadRot = playerData.lockedYaw
                        disguise.yBodyRot = playerData.lockedYaw
                    }
                }
                // NOTE: Position sync is handled by AeonisManager tick
                // The networking system properly syncs player -> entity position
            } else {
                // Disguise entity was destroyed - this shouldn't happen normally
                // but if it does, the player loses their disguise
                logger.warn("Disguise entity destroyed for player ${player.name.string}")
                disguiseEntities.remove(player.uuid)
            }
        }
        
        // Enforce freeze position
        if (playerData.movementFrozen) {
            val frozenPos = frozenPositions[player.uuid]
            if (frozenPos != null) {
                // Teleport player back to frozen position
                player.teleportTo(frozenPos.x, frozenPos.y, frozenPos.z)
                // Also update the controlled entity position
                val disguise = entityId?.let { level.getEntity(it) }
                disguise?.setPos(frozenPos.x, frozenPos.y, frozenPos.z)
            }
        }
        
        // Show actionbar with status
        showPropActionBar(player, playerData, game)
    }
    
    /**
     * Shows actionbar information to prop players.
     */
    private fun showPropActionBar(player: ServerPlayer, data: PropHuntPlayerData, game: PropHuntGame) {
        val statusParts = mutableListOf<String>()
        
        // Disguise type
        val disguiseName = data.disguiseType?.substringAfterLast(":") ?: "none"
        statusParts.add("§7Disguise: §e$disguiseName")
        
        // Lock status
        if (data.rotationLocked) statusParts.add("§6🔒 Locked")
        
        // Freeze status
        if (data.movementFrozen) statusParts.add("§b❄ Frozen")
        
        // Taunt cooldown
        if (data.tauntCooldown > 0) {
            val seconds = data.tauntCooldown / 20
            statusParts.add("§d🎭 ${seconds}s")
        } else {
            statusParts.add("§a🎭 Ready")
        }
        
        val message = statusParts.joinToString(" §8| ")
        player.displayClientMessage(Component.literal(message), true)
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // HIT DETECTION & REVEAL
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Called when a prop is caught by a hunter.
     */
    fun onPropCaught(prop: ServerPlayer, hunter: ServerPlayer, game: PropHuntGame) {
        val propData = game.players[prop.uuid] ?: return
        val hunterData = game.players[hunter.uuid] ?: return
        
        if (!propData.isAlive) return
        
        // Mark prop as eliminated
        propData.isAlive = false
        hunterData.propsFound++
        
        // Record statistics
        PropHuntManager.statistics.recordPropFound(hunter.uuid)
        PropHuntManager.statistics.recordSurvivalTime(prop.uuid, propData.survivalTicks.toLong())
        
        // Remove disguise and reveal
        undisguise(prop)
        
        // Set to spectator
        prop.setGameMode(GameType.SPECTATOR)
        
        // Award XP to hunter
        hunter.giveExperiencePoints(game.settings.hunterFindRewardXP)
        
        // Dramatic reveal effects
        val level = prop.level() as? ServerLevel
        if (level != null) {
            // Death particles
            level.sendParticles(
                ParticleTypes.EXPLOSION,
                prop.x, prop.y + 1, prop.z,
                5, 0.5, 0.5, 0.5, 0.0
            )
            
            // Sound
            level.playSound(null, prop.x, prop.y, prop.z,
                SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f)
        }
        
        // Announce
        game.broadcast("§c§l💀 §e${prop.name.string}§7 (§a${propData.disguiseType?.substringAfterLast(":")}§7) was found by §c${hunter.name.string}§7!")
        
        // Count remaining props
        val remainingProps = game.getProps().count { it.isAlive }
        game.broadcast("§7Remaining props: §e$remainingProps")
        
        // Tips for spectating
        prop.sendSystemMessage(Component.literal("§7You are now spectating. You can fly around and watch!"))
    }
    
    /**
     * Called when a hunter hits a non-prop entity (false positive).
     */
    fun onFalseHit(hunter: ServerPlayer, hitEntity: Entity, game: PropHuntGame) {
        val hunterData = game.players[hunter.uuid] ?: return
        
        hunterData.falseHits++
        
        // Penalty - take damage
        hunter.hurt(hunter.damageSources().magic(), game.settings.falseHitPenaltyHealth)
        
        // Effects
        val level = hunter.level() as? ServerLevel
        if (level != null) {
            level.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                hitEntity.x, hitEntity.y + 1, hitEntity.z,
                10, 0.5, 0.5, 0.5, 0.0
            )
        }
        
        hunter.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f)
        hunter.sendSystemMessage(Component.literal("§c[PropHunt] §7That wasn't a prop! §c-${game.settings.falseHitPenaltyHealth}§4❤"))
        
        // Announce to props (schadenfreude!)
        game.broadcastToProps("§a[PropHunt] §7A hunter hit a fake! They lost health!")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // PARTICLE EFFECTS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun spawnTransformParticles(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        level.sendParticles(
            ParticleTypes.WITCH,
            player.x, player.y + 1, player.z,
            30, 0.5, 1.0, 0.5, 0.1
        )
    }
    
    private fun spawnFreezeParticles(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        level.sendParticles(
            ParticleTypes.SNOWFLAKE,
            player.x, player.y + 0.5, player.z,
            20, 0.3, 0.5, 0.3, 0.02
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Checks if a player is currently disguised.
     */
    fun isDisguised(player: ServerPlayer): Boolean {
        return disguiseEntities.containsKey(player.uuid)
    }
    
    /**
     * Gets the disguise entity for a player.
     */
    fun getDisguiseEntity(player: ServerPlayer): Entity? {
        val entityId = disguiseEntities[player.uuid] ?: return null
        val level = player.level() as? ServerLevel ?: return null
        return level.getEntity(entityId)
    }
    
    /**
     * Checks if an entity is a prop disguise.
     */
    fun isPropDisguise(entity: Entity): UUID? {
        val name = entity.customName?.string ?: return null
        if (name.startsWith("§8[PH:") && name.endsWith("]")) {
            val uuidStr = name.removePrefix("§8[PH:").removeSuffix("]")
            return try {
                UUID.fromString(uuidStr)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
    
    /**
     * Shows control tips to a prop player.
     */
    fun showPropTips(player: ServerPlayer) {
        player.sendSystemMessage(Component.literal(""))
        player.sendSystemMessage(Component.literal("§a§l═══════ PROP CONTROLS ═══════"))
        player.sendSystemMessage(Component.literal("§7• §eRight-click Compass§7 → Lock/Unlock rotation"))
        player.sendSystemMessage(Component.literal("§7• §eRight-click Ice§7 → Freeze/Unfreeze position"))
        player.sendSystemMessage(Component.literal("§7• §eRight-click Horn§7 → Taunt (creates decoys)"))
        player.sendSystemMessage(Component.literal("§7• §eSneak + Don't move§7 → Blend in perfectly"))
        player.sendSystemMessage(Component.literal("§a§l══════════════════════════════"))
        player.sendSystemMessage(Component.literal(""))
    }
}

/**
 * Types of taunts available to props.
 */
enum class TauntType {
    /** Plays mob sounds at random location */
    SOUND_DECOY,
    
    /** Creates particle effects elsewhere */
    PARTICLE_BURST,
    
    /** Creates footstep sounds leading away */
    FAKE_FOOTSTEPS,
    
    /** Risky laugh at current position */
    LAUGHING,
    
    /** Creates fake death effects */
    FAKE_DEATH
}
