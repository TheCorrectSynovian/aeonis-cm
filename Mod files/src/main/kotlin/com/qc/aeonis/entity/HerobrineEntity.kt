package com.qc.aeonis.entity

import com.qc.aeonis.config.AeonisFeatures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.damagesource.DamageSource
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
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.animal.Chicken
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.entity.animal.Pig
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignText
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Herobrine - The mysterious entity that appears behind players
 * 
 * Behaviors:
 * - Appears behind player (~10 blocks away)
 * - Disappears in 1-2 seconds if player turns and looks at it
 * - Can roam ~50 blocks away with a random sword
 * - Stares at player but never attacks them
 * - Can occasionally attack pigs, cows, sheep
 * - Disappears if player gets within 20 blocks
 * - Cycle repeats every 5-10 minutes
 */
class HerobrineEntity(entityType: EntityType<out HerobrineEntity>, level: Level) : PathfinderMob(entityType, level) {
    
    // Behavior state
    enum class HerobrineState {
        WATCHING_BEHIND,    // Spawned behind player, watching
        ROAMING,           // Roaming with sword in hand
        STARING,           // Just staring from a distance
        HUNTING_ANIMALS,   // Attacking passive mobs
        EGG_SPAWNED        // Special: spawned from egg, will attack once then teleport away
    }
    
    private var currentState = HerobrineState.WATCHING_BEHIND
    private var targetPlayerId: Int? = null
    private var beingWatchedTicks = 0
    private var existenceTicks = 0
    private var maxExistenceTicks = 6000 // 5 minutes default, varies by state
    private var scheduledDisappearTicks = -1
    private var hasBeenSeenOnce = false // Track if player has spotted us at least once
    
    // Track if player is looking at us
    private var playerIsLookingAtUs = false
    
    // Egg spawn special behavior
    private var eggSpawnPhase = 0 // 0 = waiting, 1 = attacked, 2 = teleported & roaming
    private var eggSpawnTicks = 0
    private var hasAttackedOnce = false
    
    // Cross building behavior
    private var hasBuiltCross = false
    private var crossBuildCooldown = 0
    
    // Leaf decay behavior (classic creepypasta)
    private var leafDecayCooldown = 0
    
    override fun registerGoals() {
        // Herobrine has custom AI - minimal standard goals
        goalSelector.addGoal(0, HerobrineStareAtPlayerGoal(this))
        goalSelector.addGoal(1, HerobrineRoamGoal(this))
        goalSelector.addGoal(2, HerobrineMeleeAttackGoal(this, 1.0, true))
        
        // Only targets animals, NEVER players
        targetSelector.addGoal(1, NearestAttackableTargetGoal(this, Pig::class.java, 10, true, false) { _, _ -> currentState == HerobrineState.HUNTING_ANIMALS })
        targetSelector.addGoal(1, NearestAttackableTargetGoal(this, Cow::class.java, 10, true, false) { _, _ -> currentState == HerobrineState.HUNTING_ANIMALS })
        targetSelector.addGoal(1, NearestAttackableTargetGoal(this, Chicken::class.java, 10, true, false) { _, _ -> currentState == HerobrineState.HUNTING_ANIMALS })
    }
    
    override fun aiStep() {
        super.aiStep()
        
        if (level().isClientSide) return
        
        val serverLevel = level() as? ServerLevel ?: return
        existenceTicks++
        
        // Handle scheduled disappearance (when player looks at us)
        if (scheduledDisappearTicks > 0) {
            scheduledDisappearTicks--
            if (scheduledDisappearTicks <= 0) {
                disappearWithEffect(serverLevel)
                return
            }
        }
        
        // Get target player
        val targetPlayer = targetPlayerId?.let { serverLevel.getEntity(it) as? Player }
        
        if (targetPlayer == null || !targetPlayer.isAlive) {
            // Player gone, disappear
            disappearWithEffect(serverLevel)
            return
        }
        
        val distanceToPlayer = distanceTo(targetPlayer)
        
        // Only disappear if player gets VERY close (within 8 blocks) and we've been seen
        // This makes Herobrine more persistent and creepy
        val minDistance = when (currentState) {
            HerobrineState.WATCHING_BEHIND -> 6.0  // Can get quite close when behind
            HerobrineState.ROAMING -> 10.0         // Disappears if player approaches while roaming
            HerobrineState.STARING -> 12.0         // Keeps distance while staring
            HerobrineState.HUNTING_ANIMALS -> 8.0  // Focused on hunting, less aware
            HerobrineState.EGG_SPAWNED -> 3.0      // Very close for egg spawn attack
        }
        
        if (distanceToPlayer < minDistance && hasBeenSeenOnce) {
            disappearWithEffect(serverLevel)
            return
        }
        
        // Check if player is looking at us
        playerIsLookingAtUs = isPlayerLookingAtMe(targetPlayer)
        
        // Handle being watched
        when (currentState) {
            HerobrineState.WATCHING_BEHIND -> {
                if (playerIsLookingAtUs) {
                    hasBeenSeenOnce = true
                    beingWatchedTicks++
                    // Only disappear after being watched for 3-5 seconds continuously
                    if (beingWatchedTicks > 60 && scheduledDisappearTicks < 0) {
                        // Schedule disappear in 40-80 ticks (2-4 seconds) - gives player time to look
                        scheduledDisappearTicks = random.nextInt(40) + 40
                    }
                } else {
                    // Slowly decay watched ticks so brief glances don't reset
                    if (beingWatchedTicks > 0) beingWatchedTicks -= 1
                }
                
                // After being watched once, might transition to STARING state
                if (hasBeenSeenOnce && beingWatchedTicks == 0 && random.nextInt(200) == 0) {
                    setState(HerobrineState.STARING)
                }
            }
            HerobrineState.STARING -> {
                // Stare at player from distance - very creepy, lasts longer
                if (playerIsLookingAtUs) {
                    hasBeenSeenOnce = true
                    beingWatchedTicks++
                    // Only disappear after 10+ seconds of continuous staring contest
                    if (beingWatchedTicks > 200) {
                        disappearWithEffect(serverLevel)
                        return
                    }
                } else {
                    // When player looks away, might teleport to a new position
                    if (beingWatchedTicks > 40 && random.nextInt(100) == 0) {
                        teleportToNewStaringPosition(serverLevel, targetPlayer)
                        beingWatchedTicks = 0
                    }
                }
            }
            HerobrineState.ROAMING -> {
                // Roam around with sword, very visible and persistent
                // Player seeing us doesn't make us disappear - we're just "passing through"
                if (playerIsLookingAtUs) {
                    hasBeenSeenOnce = true
                }
                
                // Frequently switch to hunting mode when roaming - Herobrine loves killing animals
                if (random.nextInt(150) == 0) {
                    setState(HerobrineState.HUNTING_ANIMALS)
                }
                
                // Occasionally stop and stare at player
                if (random.nextInt(600) == 0 && distanceToPlayer < 60.0) {
                    setState(HerobrineState.STARING)
                }
                
                // Maybe build a creepy cross while roaming
                if (!hasBuiltCross && crossBuildCooldown <= 0 && random.nextInt(800) == 0) {
                    buildInfestedCross(serverLevel)
                }
                if (crossBuildCooldown > 0) crossBuildCooldown--
                
                // CLASSIC CREEPYPASTA: Slowly decay leaves from nearby trees
                if (leafDecayCooldown <= 0) {
                    decayNearbyLeaves(serverLevel)
                    leafDecayCooldown = 20 + random.nextInt(40) // Every 1-3 seconds
                }
                if (leafDecayCooldown > 0) leafDecayCooldown--
            }
            HerobrineState.HUNTING_ANIMALS -> {
                // Focused on hunting, only disappears if player gets very close
                if (playerIsLookingAtUs) {
                    hasBeenSeenOnce = true
                }
                // Might look at player briefly while hunting - creepy stare
                if (random.nextInt(60) == 0) {
                    lookControl.setLookAt(targetPlayer, 30f, 30f)
                }
                
                // If no target, go back to roaming to find more animals
                if (target == null && random.nextInt(100) == 0) {
                    setState(HerobrineState.ROAMING)
                }
                
                // Maybe build a cross near a kill site
                if (!hasBuiltCross && crossBuildCooldown <= 0 && random.nextInt(400) == 0) {
                    buildInfestedCross(serverLevel)
                }
                if (crossBuildCooldown > 0) crossBuildCooldown--
            }
            HerobrineState.EGG_SPAWNED -> {
                handleEggSpawnBehavior(serverLevel, targetPlayer)
                return // EGG_SPAWNED handles its own existence
            }
        }
        
        // Max existence time check
        if (existenceTicks >= maxExistenceTicks) {
            disappearWithEffect(serverLevel)
        }
    }
    
    /**
     * Special behavior for egg-spawned Herobrine:
     * Phase 0: Wait 2 seconds (40 ticks)
     * Phase 1: Attack player/ground once, then teleport 30 blocks away
     * Phase 2: Roam for 1 minute then disappear
     */
    private fun handleEggSpawnBehavior(serverLevel: ServerLevel, targetPlayer: Player) {
        eggSpawnTicks++
        
        when (eggSpawnPhase) {
            0 -> {
                // Phase 0: Stand still and stare for 2 seconds
                lookControl.setLookAt(targetPlayer, 30f, 30f)
                navigation.stop()
                
                if (eggSpawnTicks >= 40) { // 2 seconds
                    eggSpawnPhase = 1
                }
            }
            1 -> {
                // Phase 1: Attack once then teleport
                if (!hasAttackedOnce) {
                    hasAttackedOnce = true
                    
                    // Swing arm animation
                    swing(net.minecraft.world.InteractionHand.MAIN_HAND)
                    
                    // Play attack sound
                    serverLevel.playSound(
                        null, blockPosition(),
                        SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.HOSTILE,
                        1.0f, 0.8f
                    )
                    
                    // If player is within 3 blocks, actually hit them
                    val distToPlayer = distanceTo(targetPlayer)
                    if (distToPlayer <= 3.0) {
                        targetPlayer.hurt(serverLevel.damageSources().mobAttack(this), 4.0f)
                    }
                    
                    // Schedule teleport after brief delay
                    eggSpawnTicks = 0
                }
                
                if (eggSpawnTicks >= 10) { // 0.5 second delay after attack
                    // Teleport 30 blocks away
                    teleportAway(serverLevel, targetPlayer, 30.0)
                    eggSpawnPhase = 2
                    eggSpawnTicks = 0
                    maxExistenceTicks = 1200 // 1 minute of roaming
                    existenceTicks = 0
                    equipRandomSword()
                }
            }
            2 -> {
                // Phase 2: Roam for 1 minute
                // Normal roaming behavior - don't disappear when player approaches
                if (existenceTicks >= maxExistenceTicks) {
                    disappearWithEffect(serverLevel)
                }
            }
        }
    }
    
    /**
     * Teleport to a position away from the player
     */
    private fun teleportAway(serverLevel: ServerLevel, player: Player, distance: Double) {
        // Spawn particles at current location
        serverLevel.sendParticles(
            ParticleTypes.LARGE_SMOKE,
            x, y + 1.0, z,
            15, 0.3, 0.5, 0.3, 0.02
        )
        
        // Find a position away from player
        val angle = random.nextDouble() * Math.PI * 2
        val newX = player.x + sin(angle) * distance
        val newZ = player.z + cos(angle) * distance
        val newY = findGroundLevel(serverLevel, BlockPos.containing(newX, player.y, newZ)).toDouble()
        
        if (newY > serverLevel.minY) {
            // Teleport
            teleportTo(newX, newY, newZ)
            
            // Spawn particles at new location
            serverLevel.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                newX, newY + 1.0, newZ,
                15, 0.3, 0.5, 0.3, 0.02
            )
            
            // Teleport sound
            serverLevel.playSound(
                null, BlockPos.containing(newX, newY, newZ),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE,
                0.5f, 0.7f
            )
        }
    }
    
    /**
     * Teleport to a new staring position when player looks away
     * This creates the creepy effect of Herobrine moving when you're not looking
     */
    private fun teleportToNewStaringPosition(serverLevel: ServerLevel, player: Player) {
        // Calculate a new position around the player at staring distance
        val distance = 25.0 + random.nextDouble() * 15.0 // 25-40 blocks
        val angle = random.nextDouble() * Math.PI * 2
        
        val newX = player.x + sin(angle) * distance
        val newZ = player.z + cos(angle) * distance
        val newY = findGroundLevel(serverLevel, BlockPos.containing(newX, player.y, newZ)).toDouble()
        
        if (newY > serverLevel.minY) {
            // Silent teleport - no particles, makes it creepier
            teleportTo(newX, newY, newZ)
            
            // Face the player
            val dx = player.x - newX
            val dz = player.z - newZ
            yRot = (Math.toDegrees(Math.atan2(-dx, dz))).toFloat()
        }
    }
    
    /**
     * Check if the given player is looking at this entity
     */
    private fun isPlayerLookingAtMe(player: Player): Boolean {
        val toEntity = Vec3(x - player.x, eyeY - player.eyeY, z - player.z).normalize()
        val lookVec = player.lookAngle.normalize()
        
        // Dot product - if > 0.9, player is looking roughly at us (within ~25 degrees)
        val dot = lookVec.dot(toEntity)
        
        // Also check if player can actually see us (line of sight)
        if (dot > 0.85) {
            return player.hasLineOfSight(this)
        }
        return false
    }
    
    /**
     * Build a creepy plus/cross symbol out of infested cobblestone
     * This is Herobrine's signature - leaving mysterious structures
     */
    private fun buildInfestedCross(serverLevel: ServerLevel) {
        // Find a good spot nearby on the ground
        val offsetX = random.nextInt(11) - 5  // -5 to 5
        val offsetZ = random.nextInt(11) - 5
        val basePos = BlockPos.containing(x + offsetX, y, z + offsetZ)
        
        // Find ground level at this position
        val groundY = findGroundLevel(serverLevel, basePos)
        if (groundY <= serverLevel.minY) return
        
        val crossBase = BlockPos(basePos.x, groundY, basePos.z)
        
        // Check if there's space to build (all positions should be air or replaceable)
        val crossPositions = listOf(
            crossBase,                          // Center
            crossBase.above(),                  // Up 1
            crossBase.above().above(),          // Up 2 (top)
            crossBase.north(),                  // North
            crossBase.south(),                  // South
            crossBase.east(),                   // East
            crossBase.west()                    // West
        )
        
        // Only build if most positions are air
        val airCount = crossPositions.count { serverLevel.getBlockState(it).isAir }
        if (airCount < 5) return
        
        // Build the cross with infested cobblestone
        val infestedCobble = Blocks.INFESTED_COBBLESTONE.defaultBlockState()
        
        for (pos in crossPositions) {
            if (serverLevel.getBlockState(pos).isAir) {
                serverLevel.setBlock(pos, infestedCobble, 3)
            }
        }
        
        hasBuiltCross = true
        crossBuildCooldown = 2400 // 2 minute cooldown before building another
        
        // Subtle particle effect
        serverLevel.sendParticles(
            ParticleTypes.SMOKE,
            crossBase.x + 0.5, crossBase.y + 1.5, crossBase.z + 0.5,
            10, 0.5, 0.5, 0.5, 0.01
        )
    }
    
    /**
     * Build a shrine cross at a kill site with meat and a sign
     * Called when Herobrine kills an animal - 50% chance
     */
    fun buildKillShrine(serverLevel: ServerLevel, killPos: BlockPos, meatItem: net.minecraft.world.item.Item) {
        if (random.nextFloat() > 0.5f) return // 50% chance
        
        // Find ground level
        val groundY = findGroundLevel(serverLevel, killPos)
        if (groundY <= serverLevel.minY) return
        
        val shrineBase = BlockPos(killPos.x, groundY, killPos.z)
        
        // Build the cross
        val infestedCobble = Blocks.INFESTED_COBBLESTONE.defaultBlockState()
        val crossPositions = listOf(
            shrineBase,
            shrineBase.above(),
            shrineBase.above().above(),
            shrineBase.north(),
            shrineBase.south(),
            shrineBase.east(),
            shrineBase.west()
        )
        
        for (pos in crossPositions) {
            if (serverLevel.getBlockState(pos).isAir) {
                serverLevel.setBlock(pos, infestedCobble, 3)
                // Register this block as a shrine block
                registerShrineBlock(serverLevel, pos)
            }
        }
        
        // Place a sign next to the cross
        val signPos = shrineBase.east().east()
        if (serverLevel.getBlockState(signPos).isAir && serverLevel.getBlockState(signPos.below()).isSolid) {
            val signState = Blocks.OAK_SIGN.defaultBlockState()
            serverLevel.setBlock(signPos, signState, 3)
            
            // Set sign text
            val signEntity = serverLevel.getBlockEntity(signPos) as? SignBlockEntity
            if (signEntity != null) {
                val text = SignText()
                    .setMessage(0, Component.literal(""))
                    .setMessage(1, Component.literal("Im watching"))
                    .setMessage(2, Component.literal(""))
                    .setMessage(3, Component.literal(""))
                signEntity.setText(text, true)
                registerShrineBlock(serverLevel, signPos)
            }
        }
        
        // Drop meat items around the shrine
        val meatStack = ItemStack(meatItem, 1 + random.nextInt(3))
        val itemEntity = net.minecraft.world.entity.item.ItemEntity(
            serverLevel,
            shrineBase.x + 0.5 + random.nextDouble() - 0.5,
            shrineBase.y + 0.5,
            shrineBase.z + 0.5 + random.nextDouble() - 0.5,
            meatStack
        )
        serverLevel.addFreshEntity(itemEntity)
        
        // Eerie particle effect
        serverLevel.sendParticles(
            ParticleTypes.SOUL,
            shrineBase.x + 0.5, shrineBase.y + 2.0, shrineBase.z + 0.5,
            15, 0.5, 1.0, 0.5, 0.02
        )
    }
    
    /**
     * CLASSIC CREEPYPASTA: Slowly decay leaves from nearby trees
     * Trees within 5 blocks slowly lose their leaves when Herobrine roams nearby
     */
    private fun decayNearbyLeaves(serverLevel: ServerLevel) {
        val searchRadius = 5
        val myPos = blockPosition()
        
        // Search for leaf blocks in a 5 block radius
        for (dx in -searchRadius..searchRadius) {
            for (dy in -2..8) { // Trees can be tall
                for (dz in -searchRadius..searchRadius) {
                    val checkPos = myPos.offset(dx, dy, dz)
                    val state = serverLevel.getBlockState(checkPos)
                    
                    // Check if it's a leaf block
                    if (state.`is`(BlockTags.LEAVES)) {
                        // Small chance to decay each leaf (creates slow decay effect)
                        if (random.nextInt(200) == 0) {
                            // Remove the leaf with particle effect
                            serverLevel.destroyBlock(checkPos, false)
                            
                            // Subtle leaf particle
                            serverLevel.sendParticles(
                                ParticleTypes.COMPOSTER,
                                checkPos.x + 0.5, checkPos.y + 0.5, checkPos.z + 0.5,
                                3, 0.3, 0.3, 0.3, 0.01
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Disappear with a spooky effect
     */
    fun disappearWithEffect(serverLevel: ServerLevel) {
        // Spawn particles
        serverLevel.sendParticles(
            ParticleTypes.LARGE_SMOKE,
            x, y + 1.0, z,
            20, 0.3, 0.5, 0.3, 0.02
        )
        
        // Subtle ambient sound
        serverLevel.playSound(
            null, blockPosition(),
            SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.HOSTILE,
            0.3f, 0.5f
        )
        
        // Remove entity
        discard()
    }
    
    override fun finalizeSpawn(
        accessor: ServerLevelAccessor,
        difficulty: DifficultyInstance,
        reason: EntitySpawnReason,
        spawnData: SpawnGroupData?
    ): SpawnGroupData? {
        val data = super.finalizeSpawn(accessor, difficulty, reason, spawnData)
        
        // If spawned from spawn egg, use special EGG_SPAWNED behavior
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE) {
            setState(HerobrineState.EGG_SPAWNED)
            
            // Find nearest player to target
            val nearestPlayer = accessor.getNearestPlayer(this, 50.0)
            if (nearestPlayer != null) {
                setTargetPlayer(nearestPlayer)
            }
        }
        
        // Give random sword based on state
        if (currentState == HerobrineState.ROAMING || currentState == HerobrineState.HUNTING_ANIMALS) {
            equipRandomSword()
        }
        
        return data
    }
    
    private fun equipRandomSword() {
        val swords = listOf(
            Items.IRON_SWORD,
            Items.DIAMOND_SWORD,
            Items.STONE_SWORD,
            Items.GOLDEN_SWORD
        )
        val sword = ItemStack(swords[random.nextInt(swords.size)])
        setItemSlot(EquipmentSlot.MAINHAND, sword)
    }
    
    fun setTargetPlayer(player: Player) {
        targetPlayerId = player.id
    }
    
    fun setState(state: HerobrineState) {
        currentState = state
        
        // Adjust existence time and equipment based on state - longer times for more fun!
        when (state) {
            HerobrineState.WATCHING_BEHIND -> {
                maxExistenceTicks = 2400 // 2 minutes - gives player time to turn around
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
            }
            HerobrineState.ROAMING -> {
                maxExistenceTicks = 6000 // 5 minutes of roaming
                equipRandomSword()
            }
            HerobrineState.STARING -> {
                maxExistenceTicks = 3600 // 3 minutes of creepy staring
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
            }
            HerobrineState.HUNTING_ANIMALS -> {
                maxExistenceTicks = 4800 // 4 minutes of hunting
                equipRandomSword()
            }
            HerobrineState.EGG_SPAWNED -> {
                maxExistenceTicks = 3600 // 3 minutes after teleport phase
                eggSpawnPhase = 0
                eggSpawnTicks = 0
                hasAttackedOnce = false
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY) // No weapon initially
            }
        }
    }
    
    fun getCurrentState() = currentState
    fun getTargetPlayerId() = targetPlayerId
    
    // Herobrine takes no knockback and is silent
    override fun isPushable(): Boolean = false
    override fun canBeCollidedWith(entity: Entity?): Boolean = false
    
    // Never hurt by player directly
    override fun isInvulnerableTo(level: ServerLevel, source: DamageSource): Boolean {
        // Herobrine is invulnerable to player attacks
        if (source.entity is Player) return true
        return super.isInvulnerableTo(level, source)
    }
    
    // Silent footsteps
    override fun playStepSound(pos: BlockPos, block: net.minecraft.world.level.block.state.BlockState) {
        // No footstep sounds - Herobrine is silent
    }
    
    override fun getAmbientSound() = null
    override fun getHurtSound(source: DamageSource) = null
    override fun getDeathSound() = null
    
    /**
     * Called when Herobrine attacks - check if we killed the target
     */
    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        val result = super.doHurtTarget(serverLevel, target)
        
        // Check if we killed the target
        if (result && target is LivingEntity && !target.isAlive) {
            // Determine what meat to drop based on entity type
            val meatItem = when (target) {
                is Cow -> Items.BEEF
                is Pig -> Items.PORKCHOP
                is Chicken -> Items.CHICKEN
                else -> Items.ROTTEN_FLESH
            }
            
            // Build a shrine at the kill site (50% chance handled inside)
            buildKillShrine(serverLevel, target.blockPosition(), meatItem)
        }
        
        return result
    }
    
    companion object {
        // Track shrine blocks across all Herobrines for block break detection
        private val shrineBlocks = ConcurrentHashMap<String, MutableSet<BlockPos>>()
        
        /**
         * Register a block as part of a Herobrine shrine
         */
        fun registerShrineBlock(level: ServerLevel, pos: BlockPos) {
            val key = level.dimension().location().toString()
            shrineBlocks.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(pos)
        }
        
        /**
         * Check if a block is part of a shrine and spawn Herobrine if broken
         * Call this from block break events
         */
        fun onBlockBroken(level: ServerLevel, pos: BlockPos, player: Player): Boolean {
            val key = level.dimension().location().toString()
            val blocks = shrineBlocks[key] ?: return false
            
            if (blocks.remove(pos)) {
                // Player broke a shrine block! Spawn Herobrine behind them
                spawnBehindPlayerForStare(level, player)
                return true
            }
            return false
        }
        
        /**
         * Spawn Herobrine 5 blocks behind player for a 1.5 second stare
         */
        private fun spawnBehindPlayerForStare(serverLevel: ServerLevel, player: Player) {
            if (!AeonisFeatures.areExtraMobsEnabled(serverLevel)) return
            
            val herobrine = AeonisEntities.HEROBRINE.create(serverLevel, EntitySpawnReason.COMMAND) ?: return
            
            // Calculate position 5 blocks behind player
            val yaw = Math.toRadians(player.yRot.toDouble() + 180.0)
            val distance = 5.0
            
            val spawnX = player.x + sin(yaw) * distance
            val spawnZ = player.z - cos(yaw) * distance
            val spawnY = findGroundLevel(serverLevel, BlockPos.containing(spawnX, player.y, spawnZ)).toDouble()
            
            if (spawnY <= serverLevel.minY) return
            
            herobrine.setPos(spawnX, spawnY, spawnZ)
            herobrine.yRot = player.yRot // Face the player
            herobrine.xRot = 0f
            herobrine.setTargetPlayer(player)
            herobrine.setState(HerobrineState.STARING)
            herobrine.maxExistenceTicks = 30 // 1.5 seconds only!
            herobrine.hasBeenSeenOnce = true
            
            serverLevel.addFreshEntity(herobrine)
            
            // Creepy sound
            serverLevel.playSound(
                null, player.blockPosition(),
                SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.HOSTILE,
                0.5f, 0.5f
            )
        }
        
        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 100.0) // High health but usually disappears
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 6.0)
            .add(Attributes.FOLLOW_RANGE, 100.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0) // Full knockback resistance
        
        /**
         * Spawn Herobrine behind a player
         * @param avoidDirection If provided, spawn opposite to this direction (to avoid being visible with another Herobrine)
         */
        fun spawnBehindPlayer(serverLevel: ServerLevel, player: Player, state: HerobrineState, avoidDirection: Vec3? = null): HerobrineEntity? {
            if (!AeonisFeatures.areExtraMobsEnabled(serverLevel)) return null
            
            val herobrine = AeonisEntities.HEROBRINE.create(serverLevel, EntitySpawnReason.COMMAND) ?: return null
            
            // Calculate position - spawn at good visible distances
            val distance = when (state) {
                HerobrineState.WATCHING_BEHIND -> 20.0 + serverLevel.random.nextDouble() * 15.0 // 20-35 blocks behind
                HerobrineState.ROAMING -> 25.0 + serverLevel.random.nextDouble() * 25.0 // 25-50 blocks away
                HerobrineState.STARING -> 30.0 + serverLevel.random.nextDouble() * 20.0 // 30-50 blocks away
                HerobrineState.HUNTING_ANIMALS -> 20.0 + serverLevel.random.nextDouble() * 20.0 // 20-40 blocks away
                HerobrineState.EGG_SPAWNED -> 0.0 // Spawns at egg location
            }
            
            val finalYaw: Double
            if (avoidDirection != null) {
                // Spawn in the opposite direction from the existing Herobrine
                // Calculate angle opposite to avoidDirection
                val oppositeAngle = kotlin.math.atan2(-avoidDirection.x, -avoidDirection.z)
                // Add some randomness (±45 degrees) but still roughly opposite
                val angleOffset = (serverLevel.random.nextDouble() - 0.5) * Math.PI / 2
                finalYaw = oppositeAngle + angleOffset
            } else {
                // Get direction behind player (opposite of look direction)
                val yaw = Math.toRadians(player.yRot.toDouble() + 180.0)
                // Add some randomness to the angle
                val angleOffset = (serverLevel.random.nextDouble() - 0.5) * Math.PI / 3 // ±30 degrees
                finalYaw = yaw + angleOffset
            }
            
            val spawnX = player.x + sin(finalYaw) * distance
            val spawnZ = player.z - cos(finalYaw) * distance
            
            // Find valid Y position
            val spawnPos = BlockPos.containing(spawnX, player.y, spawnZ)
            val groundY = findGroundLevel(serverLevel, spawnPos)
            
            if (groundY < serverLevel.minY + 1) return null
            
            herobrine.setPos(spawnX, groundY.toDouble(), spawnZ)
            herobrine.yRot = player.yRot
            herobrine.xRot = 0f
            herobrine.setTargetPlayer(player)
            herobrine.setState(state)
            
            serverLevel.addFreshEntity(herobrine)
            
            return herobrine
        }
        
        private fun findGroundLevel(level: ServerLevel, pos: BlockPos): Int {
            var checkPos = pos.atY(level.maxY - 1)
            
            // Search downward for solid ground
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

/**
 * Goal to stare at the target player
 */
private class HerobrineStareAtPlayerGoal(private val herobrine: HerobrineEntity) : Goal() {
    
    init {
        flags = EnumSet.of(Flag.LOOK)
    }
    
    override fun canUse(): Boolean {
        // Always stare at player when not roaming
        val state = herobrine.getCurrentState()
        return herobrine.getTargetPlayerId() != null && 
               (state == HerobrineEntity.HerobrineState.WATCHING_BEHIND ||
                state == HerobrineEntity.HerobrineState.STARING ||
                state == HerobrineEntity.HerobrineState.EGG_SPAWNED)
    }
    
    override fun tick() {
        val level = herobrine.level()
        val targetId = herobrine.getTargetPlayerId() ?: return
        val player = level.getEntity(targetId) as? Player ?: return
        
        // Always look at the player - unblinking stare
        herobrine.lookControl.setLookAt(player, 30f, 30f)
    }
}

/**
 * Goal for roaming around (active in ROAMING and HUNTING_ANIMALS states)
 * Makes Herobrine walk around like he owns the place
 */
private class HerobrineRoamGoal(private val herobrine: HerobrineEntity) : RandomStrollGoal(herobrine, 0.7) {
    
    private var cooldown = 0
    
    override fun canUse(): Boolean {
        val state = herobrine.getCurrentState()
        // Roam in both ROAMING and HUNTING states
        if (state != HerobrineEntity.HerobrineState.ROAMING && 
            state != HerobrineEntity.HerobrineState.HUNTING_ANIMALS) {
            return false
        }
        
        // Reduce cooldown between walks for more active roaming
        if (cooldown > 0) {
            cooldown--
            return false
        }
        
        return super.canUse()
    }
    
    override fun stop() {
        super.stop()
        // Short cooldown before next walk (1-3 seconds)
        cooldown = 20 + herobrine.random.nextInt(40)
    }
}

/**
 * Melee attack goal that only works on animals, never players
 */
private class HerobrineMeleeAttackGoal(
    mob: PathfinderMob,
    speed: Double,
    pauseWhenIdle: Boolean
) : MeleeAttackGoal(mob, speed, pauseWhenIdle) {
    
    override fun canUse(): Boolean {
        val target = mob.target
        // Never attack players
        if (target is Player) return false
        return super.canUse()
    }
    
    override fun canContinueToUse(): Boolean {
        val target = mob.target
        if (target is Player) return false
        return super.canContinueToUse()
    }
}
