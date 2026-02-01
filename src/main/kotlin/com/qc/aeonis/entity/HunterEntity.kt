package com.qc.aeonis.entity

import com.qc.aeonis.minigame.manhunt.ManhuntManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.SpawnGroupData
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                         MANHUNT HUNTER AI                                  ║
 * ║                                                                            ║
 * ║  An intelligent AI hunter inspired by Dream's Manhunt series.              ║
 * ║  Features:                                                                 ║
 * ║  - Advanced pathfinding with block placing/breaking                        ║
 * ║  - Tracks and hunts registered speedrunners                                ║
 * ║  - Special abilities (sprint burst, leap, rage mode, etc.)                 ║
 * ║  - Difficulty-based speed and efficiency                                   ║
 * ║  - Teleportation for render distance issues                                ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
class HunterEntity(entityType: EntityType<out HunterEntity>, level: Level) : PathfinderMob(entityType, level) {

    // ════════════════════════════════════════════════════════════════════════
    // HUNTER STATE
    // ════════════════════════════════════════════════════════════════════════
    
    enum class HunterState {
        IDLE,           // Waiting for game to start
        HUNTING,        // Actively chasing players
        BUILDING,       // Placing blocks to climb
        BREAKING,       // Breaking blocks to reach target
        BRIDGING,       // Building bridges over gaps
        RAGING,         // Rage mode - increased speed and damage
        FROZEN          // Deactivated via /hunter deactivate
    }
    
    var currentState = HunterState.IDLE
        private set
    
    // Game reference
    var gameId: String? = null
    
    // Target management
    private var currentTargetUUID: UUID? = null
    private var lastKnownTargetPos: BlockPos? = null
    private var ticksSinceLastSeen = 0
    
    // Difficulty settings (set by game)
    var difficultyMultiplier = 1.0f // 0.5 = easy, 1.0 = normal, 1.5 = hard, 2.0 = nightmare
    
    // Block interaction cooldowns (in ticks, affected by difficulty)
    private var blockPlaceCooldown = 0
    private var blockBreakCooldown = 0
    private var blockBreakProgress = 0
    private var currentBreakingPos: BlockPos? = null
    
    // Base cooldowns (modified by difficulty)
    private val baseBlockPlaceCooldown get() = (10 / difficultyMultiplier).toInt().coerceAtLeast(2)
    private val baseBlockBreakCooldown get() = (15 / difficultyMultiplier).toInt().coerceAtLeast(3)
    
    // Inventory - blocks the hunter can place
    private val blockInventory = mutableMapOf<BlockState, Int>()
    private val maxBlocks = 128
    
    // Special abilities
    private var sprintBurstCooldown = 0
    private var leapCooldown = 0
    private var rageModeTimer = 0
    private var rageCooldown = 0
    private var lastAbilityUseTick = 0L
    
    // Teleportation for render distance
    private var ticksTargetOutOfRange = 0
    private val teleportThreshold = 200 // 10 seconds out of range triggers teleport
    
    // Combat
    private var attackCooldown = 0
    private var comboCount = 0
    private var lastAttackTick = 0L
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════════
    
    override fun registerGoals() {
        // Custom AI goals for manhunt behavior
        goalSelector.addGoal(0, HunterFreezeGoal(this))
        goalSelector.addGoal(1, HunterMeleeAttackGoal(this, 1.2, true))
        goalSelector.addGoal(2, HunterChaseGoal(this))
        goalSelector.addGoal(3, HunterBuildGoal(this))
        goalSelector.addGoal(4, HunterBreakGoal(this))
    }
    
    override fun finalizeSpawn(
        accessor: ServerLevelAccessor,
        difficulty: DifficultyInstance,
        reason: EntitySpawnReason,
        spawnData: SpawnGroupData?
    ): SpawnGroupData? {
        val data = super.finalizeSpawn(accessor, difficulty, reason, spawnData)
        
        // Give hunter starting equipment
        equipHunter()
        
        // Initialize block inventory
        initializeBlockInventory()
        
        return data
    }
    
    private fun equipHunter() {
        // Iron sword for hunting
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
        
        // Iron armor for survivability
        setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET))
        setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.IRON_CHESTPLATE))
        setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.IRON_LEGGINGS))
        setItemSlot(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS))
    }
    
    private fun initializeBlockInventory() {
        // Give hunter building blocks
        blockInventory.clear()
        blockInventory[Blocks.COBBLESTONE.defaultBlockState()] = 64
        blockInventory[Blocks.DIRT.defaultBlockState()] = 32
        blockInventory[Blocks.OAK_PLANKS.defaultBlockState()] = 32
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // MAIN AI TICK
    // ════════════════════════════════════════════════════════════════════════
    
    override fun aiStep() {
        super.aiStep()
        
        if (level().isClientSide) return
        
        val serverLevel = level() as? ServerLevel ?: return
        
        // Handle frozen state
        if (currentState == HunterState.FROZEN) {
            navigation.stop()
            return
        }
        
        // Update cooldowns
        updateCooldowns()
        
        // Update rage mode
        if (rageModeTimer > 0) {
            rageModeTimer--
            if (rageModeTimer <= 0) {
                exitRageMode()
            }
        }
        
        // Find and update target
        updateTarget(serverLevel)
        
        // Handle special abilities
        handleSpecialAbilities(serverLevel)
        
        // Handle render distance teleportation
        handleRenderDistanceTeleport(serverLevel)
        
        // Spawn hunting particles
        if (currentState == HunterState.HUNTING && tickCount % 10 == 0) {
            serverLevel.sendParticles(
                if (rageModeTimer > 0) ParticleTypes.FLAME else ParticleTypes.SMOKE,
                x, y + 0.5, z,
                3, 0.2, 0.2, 0.2, 0.01
            )
        }
    }
    
    private fun updateCooldowns() {
        if (blockPlaceCooldown > 0) blockPlaceCooldown--
        if (blockBreakCooldown > 0) blockBreakCooldown--
        if (sprintBurstCooldown > 0) sprintBurstCooldown--
        if (leapCooldown > 0) leapCooldown--
        if (rageCooldown > 0) rageCooldown--
        if (attackCooldown > 0) attackCooldown--
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TARGET MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    private fun updateTarget(serverLevel: ServerLevel) {
        val game = gameId?.let { ManhuntManager.getGame(it) }
        if (game == null || currentState == HunterState.IDLE) {
            target = null
            return
        }
        
        // Get list of alive speedrunners
        val speedrunners = game.getAliveSpeedrunners()
            .mapNotNull { serverLevel.server?.playerList?.getPlayer(it) }
            .filter { it.isAlive && !it.isSpectator }
        
        if (speedrunners.isEmpty()) {
            target = null
            currentTargetUUID = null
            return
        }
        
        // Find closest speedrunner or stick with current target
        val currentTarget = currentTargetUUID?.let { uuid ->
            speedrunners.find { it.uuid == uuid }
        }
        
        val newTarget = if (currentTarget != null && currentTarget.isAlive) {
            // Stick with current target unless someone is much closer
            val currentDist = distanceToSqr(currentTarget)
            val closer = speedrunners.filter { it != currentTarget }
                .minByOrNull { distanceToSqr(it) }
            
            if (closer != null && distanceToSqr(closer) < currentDist * 0.5) {
                closer // Switch to much closer target
            } else {
                currentTarget
            }
        } else {
            // Find new closest target
            speedrunners.minByOrNull { distanceToSqr(it) }
        }
        
        if (newTarget != null) {
            target = newTarget
            currentTargetUUID = newTarget.uuid
            lastKnownTargetPos = newTarget.blockPosition()
            ticksSinceLastSeen = 0
            
            if (currentState == HunterState.IDLE) {
                setState(HunterState.HUNTING)
            }
        } else {
            ticksSinceLastSeen++
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // BLOCK INTERACTION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Attempt to place a block at the given position
     */
    fun tryPlaceBlock(pos: BlockPos): Boolean {
        if (blockPlaceCooldown > 0) return false
        if (level().isClientSide) return false
        
        val serverLevel = level() as? ServerLevel ?: return false
        
        // Check if position is air and we have blocks
        if (!serverLevel.getBlockState(pos).isAir) return false
        
        // Find a block to place
        val blockToPlace = blockInventory.entries.firstOrNull { it.value > 0 }
        if (blockToPlace == null) return false
        
        // Check if there's a supporting block nearby
        val hasSupport = Direction.entries.any { dir ->
            val supportPos = pos.relative(dir)
            serverLevel.getBlockState(supportPos).isSolid
        }
        
        if (!hasSupport) return false
        
        // Place the block
        serverLevel.setBlock(pos, blockToPlace.key, 3)
        blockInventory[blockToPlace.key] = blockToPlace.value - 1
        
        // Play sound and particles
        serverLevel.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f)
        
        blockPlaceCooldown = baseBlockPlaceCooldown
        
        // Swing arm animation
        swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        
        return true
    }
    
    /**
     * Attempt to break a block at the given position
     */
    fun tryBreakBlock(pos: BlockPos): Boolean {
        if (blockBreakCooldown > 0 && currentBreakingPos != pos) return false
        if (level().isClientSide) return false
        
        val serverLevel = level() as? ServerLevel ?: return false
        val blockState = serverLevel.getBlockState(pos)
        
        // Can't break air, bedrock, or reinforced blocks
        if (blockState.isAir) return false
        if (blockState.getDestroySpeed(serverLevel, pos) < 0) return false
        if (blockState.`is`(BlockTags.FEATURES_CANNOT_REPLACE)) return false
        
        // Start or continue breaking
        if (currentBreakingPos != pos) {
            currentBreakingPos = pos
            blockBreakProgress = 0
        }
        
        // Calculate break time based on block hardness and difficulty
        val hardness = blockState.getDestroySpeed(serverLevel, pos)
        val breakTime = ((hardness * 30) / difficultyMultiplier).toInt().coerceAtLeast(5)
        
        blockBreakProgress++
        
        // Show breaking progress particles
        if (blockBreakProgress % 5 == 0) {
            serverLevel.sendParticles(
                ParticleTypes.CRIT,
                pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                5, 0.3, 0.3, 0.3, 0.1
            )
            swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        }
        
        // Break the block when progress is complete
        if (blockBreakProgress >= breakTime) {
            // Destroy block
            serverLevel.destroyBlock(pos, false)
            
            // Maybe collect the block
            if (getTotalBlocks() < maxBlocks && random.nextFloat() < 0.5f) {
                val cobble = Blocks.COBBLESTONE.defaultBlockState()
                blockInventory[cobble] = (blockInventory[cobble] ?: 0) + 1
            }
            
            currentBreakingPos = null
            blockBreakProgress = 0
            blockBreakCooldown = baseBlockBreakCooldown
            
            return true
        }
        
        return false
    }
    
    private fun getTotalBlocks(): Int = blockInventory.values.sum()
    
    /**
     * Build up towards target (tower up)
     */
    fun buildTowardsTarget(): Boolean {
        val targetEntity = target ?: return false
        val targetPos = targetEntity.blockPosition()
        val myPos = blockPosition()
        
        // If target is significantly higher, build up
        if (targetPos.y > myPos.y + 2) {
            // Place block below feet while jumping
            val placePos = myPos.below()
            if (level().getBlockState(placePos).isAir && onGround()) {
                jumpFromGround()
                return tryPlaceBlock(placePos)
            }
        }
        
        return false
    }
    
    /**
     * Build bridge towards target
     */
    fun buildBridgeTowards(targetPos: BlockPos): Boolean {
        val myPos = blockPosition()
        
        // Calculate direction to target
        val dx = (targetPos.x - myPos.x).coerceIn(-1, 1)
        val dz = (targetPos.z - myPos.z).coerceIn(-1, 1)
        
        val bridgePos = myPos.offset(dx, -1, dz)
        
        if (level().getBlockState(bridgePos).isAir) {
            return tryPlaceBlock(bridgePos)
        }
        
        return false
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // SPECIAL ABILITIES
    // ════════════════════════════════════════════════════════════════════════
    
    private fun handleSpecialAbilities(serverLevel: ServerLevel) {
        val targetEntity = target as? Player ?: return
        val distanceToTarget = distanceTo(targetEntity)
        
        // Sprint Burst - when target is medium range
        if (distanceToTarget in 10.0..30.0 && sprintBurstCooldown <= 0 && currentState == HunterState.HUNTING) {
            activateSprintBurst(serverLevel)
        }
        
        // Leap - when target is close and hunter can see them
        if (distanceToTarget in 4.0..8.0 && leapCooldown <= 0 && hasLineOfSight(targetEntity) && onGround()) {
            performLeap(serverLevel, targetEntity)
        }
        
        // Rage Mode - when low health or chasing for too long
        if (rageCooldown <= 0 && rageModeTimer <= 0) {
            if (health < maxHealth * 0.3f || ticksSinceLastSeen > 600) {
                activateRageMode(serverLevel)
            }
        }
    }
    
    private fun activateSprintBurst(serverLevel: ServerLevel) {
        // Speed boost for 5 seconds
        addEffect(MobEffectInstance(MobEffects.SPEED, 100, 1, false, true))
        
        sprintBurstCooldown = (400 / difficultyMultiplier).toInt() // 20 seconds base cooldown
        
        // Visual effect
        serverLevel.sendParticles(
            ParticleTypes.CLOUD,
            x, y + 0.5, z,
            10, 0.3, 0.2, 0.3, 0.1
        )
        
        serverLevel.playSound(null, blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.HOSTILE, 0.8f, 1.2f)
        
        // Alert nearby players
        broadcastToNearbyPlayers(serverLevel, "§c§l⚡ The Hunter activates Sprint Burst!")
    }
    
    private fun performLeap(serverLevel: ServerLevel, targetEntity: Entity) {
        // Calculate leap vector towards target
        val direction = Vec3(
            targetEntity.x - x,
            (targetEntity.y - y) + 0.5,
            targetEntity.z - z
        ).normalize()
        
        val leapStrength = 1.2 * difficultyMultiplier
        deltaMovement = Vec3(
            direction.x * leapStrength,
            0.6,
            direction.z * leapStrength
        )
        
        hurtMarked = true
        leapCooldown = (200 / difficultyMultiplier).toInt() // 10 seconds base cooldown
        
        // Visual effect
        serverLevel.sendParticles(
            ParticleTypes.SWEEP_ATTACK,
            x, y + 1.0, z,
            5, 0.5, 0.3, 0.5, 0.0
        )
        
        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 0.6f, 1.5f)
    }
    
    private fun activateRageMode(serverLevel: ServerLevel) {
        rageModeTimer = (400 * difficultyMultiplier).toInt() // 20 seconds base duration
        rageCooldown = 2400 // 2 minute cooldown
        
        // Powerful buffs
        addEffect(MobEffectInstance(MobEffects.SPEED, rageModeTimer, 2, false, true))
        addEffect(MobEffectInstance(MobEffects.STRENGTH, rageModeTimer, 1, false, true))
        addEffect(MobEffectInstance(MobEffects.REGENERATION, rageModeTimer, 0, false, true))
        
        // Upgrade sword temporarily
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.DIAMOND_SWORD))
        
        // Dramatic effect
        serverLevel.sendParticles(
            ParticleTypes.FLAME,
            x, y + 1.0, z,
            30, 0.5, 1.0, 0.5, 0.1
        )
        
        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.0f, 0.8f)
        
        // Alert all players in game
        broadcastToNearbyPlayers(serverLevel, "§4§l🔥 THE HUNTER HAS ENTERED RAGE MODE! 🔥")
        
        currentState = HunterState.RAGING
    }
    
    private fun exitRageMode() {
        // Reset to normal equipment
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
        
        if (currentState == HunterState.RAGING) {
            currentState = HunterState.HUNTING
        }
        
        val serverLevel = level() as? ServerLevel ?: return
        broadcastToNearbyPlayers(serverLevel, "§e⚡ The Hunter's rage subsides...")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // RENDER DISTANCE TELEPORTATION
    // ════════════════════════════════════════════════════════════════════════
    
    private fun handleRenderDistanceTeleport(serverLevel: ServerLevel) {
        val targetEntity = target as? ServerPlayer ?: return
        val distanceToTarget = distanceTo(targetEntity)
        
        // If target is beyond render distance (typically 128+ blocks)
        if (distanceToTarget > 100.0) {
            ticksTargetOutOfRange++
            
            if (ticksTargetOutOfRange >= teleportThreshold) {
                // Teleport to chunk near target
                teleportNearTarget(serverLevel, targetEntity)
                ticksTargetOutOfRange = 0
            }
        } else {
            ticksTargetOutOfRange = 0
        }
    }
    
    private fun teleportNearTarget(serverLevel: ServerLevel, targetEntity: Player) {
        // Calculate position ~50 blocks from target in a random direction
        val angle = random.nextDouble() * Math.PI * 2
        val distance = 40.0 + random.nextDouble() * 20.0
        
        val newX = targetEntity.x + sin(angle) * distance
        val newZ = targetEntity.z + cos(angle) * distance
        val newY = findGroundLevel(serverLevel, BlockPos.containing(newX, targetEntity.y, newZ)).toDouble()
        
        if (newY > serverLevel.minY) {
            // Spawn particles at old location
            serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                x, y + 1.0, z,
                30, 0.5, 1.0, 0.5, 0.5
            )
            
            // Teleport
            teleportTo(newX, newY, newZ)
            
            // Spawn particles at new location
            serverLevel.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                newX, newY + 1.0, newZ,
                30, 0.5, 1.0, 0.5, 0.5
            )
            
            // Dramatic sound
            serverLevel.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.5f)
            
            // Warn the player
            (targetEntity as? ServerPlayer)?.sendSystemMessage(
                Component.literal("§c§l⚠ You feel a dark presence closing in...")
            )
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // COMBAT
    // ════════════════════════════════════════════════════════════════════════
    
    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        if (attackCooldown > 0) return false
        
        // Calculate damage based on difficulty and rage mode
        var damage = getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat()
        
        if (rageModeTimer > 0) {
            damage *= 1.5f
        }
        
        damage *= difficultyMultiplier
        
        // Combo system - consecutive hits deal more damage
        val currentTick = serverLevel.gameTime
        if (currentTick - lastAttackTick < 40) {
            comboCount++
            damage += comboCount * 0.5f
            
            if (comboCount >= 3) {
                // Knockback combo finisher
                val knockback = Vec3(
                    target.x - x,
                    0.5,
                    target.z - z
                ).normalize().scale(1.5)
                
                target.deltaMovement = target.deltaMovement.add(knockback)
                
                serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    target.x, target.y + 1.0, target.z,
                    10, 0.3, 0.3, 0.3, 0.2
                )
                
                comboCount = 0
            }
        } else {
            comboCount = 1
        }
        
        lastAttackTick = currentTick
        attackCooldown = (15 / difficultyMultiplier).toInt().coerceAtLeast(5)
        
        // Reset invulnerability to allow hit
        target.invulnerableTime = 0
        
        // Deal damage
        target.hurt(serverLevel.damageSources().mobAttack(this), damage)
        
        // Swing arm animation
        swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        
        // Play attack sound
        serverLevel.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 1.0f)
        
        return true
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    fun setState(newState: HunterState) {
        val oldState = currentState
        currentState = newState
        
        val serverLevel = level() as? ServerLevel ?: return
        
        when (newState) {
            HunterState.HUNTING -> {
                if (oldState == HunterState.IDLE || oldState == HunterState.FROZEN) {
                    broadcastToNearbyPlayers(serverLevel, "§c§l🎯 The Hunt begins!")
                }
            }
            HunterState.FROZEN -> {
                navigation.stop()
                broadcastToNearbyPlayers(serverLevel, "§e⏸ The Hunter has been deactivated.")
            }
            HunterState.RAGING -> {
                // Handled in activateRageMode
            }
            else -> {}
        }
    }
    
    fun freeze() {
        setState(HunterState.FROZEN)
    }
    
    fun unfreeze() {
        if (currentState == HunterState.FROZEN) {
            setState(HunterState.HUNTING)
            val serverLevel = level() as? ServerLevel ?: return
            broadcastToNearbyPlayers(serverLevel, "§c§l▶ The Hunter has been reactivated!")
        }
    }
    
    fun startHunting() {
        initializeBlockInventory()
        setState(HunterState.HUNTING)
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════
    
    private fun broadcastToNearbyPlayers(serverLevel: ServerLevel, message: String) {
        serverLevel.players().filter { distanceTo(it) < 200.0 }.forEach { player ->
            player.sendSystemMessage(Component.literal(message))
        }
    }
    
    fun giveBlocks(count: Int) {
        val cobble = Blocks.COBBLESTONE.defaultBlockState()
        blockInventory[cobble] = (blockInventory[cobble] ?: 0) + count
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // DAMAGE HANDLING
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Handle incoming damage - reduce based on difficulty for tankier hunter
     */
    fun onHurt(source: DamageSource, amount: Float) {
        // Getting hurt might trigger rage mode
        if (health < maxHealth * 0.3f && rageCooldown <= 0 && rageModeTimer <= 0) {
            val serverLevel = level() as? ServerLevel
            if (serverLevel != null) {
                activateRageMode(serverLevel)
            }
        }
    }
    
    // Override damage to reduce based on difficulty
    override fun isInvulnerableTo(level: ServerLevel, source: DamageSource): Boolean {
        return super.isInvulnerableTo(level, source)
    }
    
    // No knockback - hunter is relentless
    override fun knockback(strength: Double, x: Double, z: Double) {
        // Reduced knockback based on difficulty
        super.knockback(strength / (difficultyMultiplier + 0.5), x, z)
    }
    
    override fun isPushable(): Boolean = false
    
    // ════════════════════════════════════════════════════════════════════════
    // COMPANION OBJECT
    // ════════════════════════════════════════════════════════════════════════
    
    companion object {
        // Track all active hunters
        private val activeHunters = ConcurrentHashMap<String, Int>() // gameId -> entityId
        
        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 60.0)
            .add(Attributes.MOVEMENT_SPEED, 0.35)
            .add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.FOLLOW_RANGE, 200.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
            .add(Attributes.ARMOR, 8.0)
        
        fun spawnHunter(serverLevel: ServerLevel, pos: BlockPos, gameId: String, difficulty: Float): HunterEntity? {
            val hunter = AeonisEntities.HUNTER.create(serverLevel, EntitySpawnReason.COMMAND) ?: return null
            
            hunter.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            hunter.gameId = gameId
            hunter.difficultyMultiplier = difficulty
            
            // Adjust attributes based on difficulty
            hunter.getAttribute(Attributes.MAX_HEALTH)?.baseValue = 60.0 * difficulty
            hunter.health = hunter.maxHealth
            hunter.getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue = 0.32 + (difficulty * 0.03)
            hunter.getAttribute(Attributes.ATTACK_DAMAGE)?.baseValue = 4.0 + (difficulty * 2.0)
            
            serverLevel.addFreshEntity(hunter)
            activeHunters[gameId] = hunter.id
            
            return hunter
        }
        
        fun getHunterForGame(gameId: String): HunterEntity? {
            val entityId = activeHunters[gameId] ?: return null
            // Would need to find entity in level - handled by ManhuntManager
            return null
        }
        
        fun removeHunterForGame(gameId: String) {
            activeHunters.remove(gameId)
        }
        
        private fun findGroundLevel(level: ServerLevel, pos: BlockPos): Int {
            var checkPos = pos.atY(level.maxY - 1)
            
            while (checkPos.y > level.minY) {
                val blockState = level.getBlockState(checkPos)
                val belowState = level.getBlockState(checkPos.below())
                
                if (blockState.isAir && belowState.isSolid) {
                    return checkPos.y
                }
                checkPos = checkPos.below()
            }
            
            return level.minY
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// AI GOALS
// ════════════════════════════════════════════════════════════════════════════

/**
 * Goal that freezes the hunter when in FROZEN state
 */
private class HunterFreezeGoal(private val hunter: HunterEntity) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)
    }
    
    override fun canUse(): Boolean = hunter.currentState == HunterEntity.HunterState.FROZEN
    
    override fun start() {
        hunter.navigation.stop()
    }
    
    override fun tick() {
        hunter.navigation.stop()
    }
}

/**
 * Custom chase goal with advanced pathfinding
 */
private class HunterChaseGoal(private val hunter: HunterEntity) : Goal() {
    private var pathRecalcCooldown = 0
    
    init {
        flags = EnumSet.of(Flag.MOVE)
    }
    
    override fun canUse(): Boolean {
        return hunter.currentState == HunterEntity.HunterState.HUNTING ||
               hunter.currentState == HunterEntity.HunterState.RAGING
    }
    
    override fun tick() {
        val target = hunter.target ?: return
        
        pathRecalcCooldown--
        if (pathRecalcCooldown <= 0) {
            val speed = if (hunter.currentState == HunterEntity.HunterState.RAGING) 1.5 else 1.2
            hunter.navigation.moveTo(target, speed * hunter.difficultyMultiplier)
            pathRecalcCooldown = 10
        }
    }
}

/**
 * Goal for placing blocks to reach higher targets
 */
private class HunterBuildGoal(private val hunter: HunterEntity) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }
    
    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        
        val target = hunter.target ?: return false
        val heightDiff = target.y - hunter.y
        
        // Need to build up if target is significantly higher
        return heightDiff > 3.0 && hunter.navigation.isDone
    }
    
    override fun start() {
        hunter.setState(HunterEntity.HunterState.BUILDING)
    }
    
    override fun tick() {
        hunter.buildTowardsTarget()
    }
    
    override fun stop() {
        if (hunter.currentState == HunterEntity.HunterState.BUILDING) {
            hunter.setState(HunterEntity.HunterState.HUNTING)
        }
    }
}

/**
 * Goal for breaking blocks that obstruct path
 */
private class HunterBreakGoal(private val hunter: HunterEntity) : Goal() {
    private var stuckTicks = 0
    private var lastPos: Vec3? = null
    
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }
    
    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        if (hunter.target == null) return false
        
        // Check if we're stuck (not moving but should be)
        val currentPos = hunter.position()
        val last = lastPos
        
        if (last != null && currentPos.distanceToSqr(last) < 0.01 && !hunter.navigation.isDone) {
            stuckTicks++
        } else {
            stuckTicks = 0
        }
        
        lastPos = currentPos
        
        return stuckTicks > 20 // Stuck for 1 second
    }
    
    override fun start() {
        hunter.setState(HunterEntity.HunterState.BREAKING)
    }
    
    override fun tick() {
        val target = hunter.target ?: return
        
        // Find block to break towards target
        val direction = Vec3(
            target.x - hunter.x,
            target.y - hunter.y,
            target.z - hunter.z
        ).normalize()
        
        val breakPos = hunter.blockPosition().offset(
            direction.x.toInt().coerceIn(-1, 1),
            direction.y.toInt().coerceIn(-1, 1),
            direction.z.toInt().coerceIn(-1, 1)
        )
        
        val level = hunter.level()
        if (!level.getBlockState(breakPos).isAir) {
            hunter.tryBreakBlock(breakPos)
            hunter.lookControl.setLookAt(breakPos.x + 0.5, breakPos.y + 0.5, breakPos.z + 0.5)
        } else {
            // Try adjacent blocks
            for (dir in Direction.entries) {
                val adjPos = hunter.blockPosition().relative(dir)
                if (!level.getBlockState(adjPos).isAir) {
                    hunter.tryBreakBlock(adjPos)
                    hunter.lookControl.setLookAt(adjPos.x + 0.5, adjPos.y + 0.5, adjPos.z + 0.5)
                    break
                }
            }
        }
    }
    
    override fun stop() {
        stuckTicks = 0
        if (hunter.currentState == HunterEntity.HunterState.BREAKING) {
            hunter.setState(HunterEntity.HunterState.HUNTING)
        }
    }
}

/**
 * Enhanced melee attack for the hunter
 */
private class HunterMeleeAttackGoal(
    private val hunter: HunterEntity,
    speed: Double,
    pauseWhenIdle: Boolean
) : MeleeAttackGoal(hunter, speed, pauseWhenIdle) {
    
    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        return super.canUse()
    }
    
    override fun canContinueToUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        return super.canContinueToUse()
    }
}
