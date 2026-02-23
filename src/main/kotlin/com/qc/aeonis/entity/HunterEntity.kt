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
import net.minecraft.world.InteractionHand
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
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                   MANHUNT HUNTER — REMASTERED ADVANCED AI                  ║
 * ║                                                                            ║
 * ║  A relentless AI hunter for the Manhunt minigame featuring:                ║
 * ║  • Movement-prediction intercepting (go where player WILL be)              ║
 * ║  • Tactical block placement (walling, bridging, towering)                  ║
 * ║  • Multi-layer stuck detection with aggressive recovery                    ║
 * ║  • Critical-hit combo combat with knockback direction control              ║
 * ║  • Sprint burst, leap, and rage mode special abilities                     ║
 * ║  • Anti-cheese: counters towering, pillaring, water/lava tricks            ║
 * ║  • Pathfinding malus for hazard avoidance                                  ║
 * ║  • Auto-crafting resource economy                                          ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
class HunterEntity(
    entityType: EntityType<out HunterEntity>,
    level: Level
) : PathfinderMob(entityType, level) {

    // ═══════════════════════════════════════════════════════════════════════
    // HUNTER STATE
    // ═══════════════════════════════════════════════════════════════════════

    enum class HunterState {
        IDLE,
        HUNTING,
        GATHERING,
        BUILDING,
        BREAKING,
        BRIDGING,
        RAGING,
        FROZEN
    }

    var currentState = HunterState.IDLE
        private set

    // ═══════════════════════════════════════════════════════════════════════
    // GAME / TARGET
    // ═══════════════════════════════════════════════════════════════════════

    var gameId: String? = null

    private var currentTargetUUID: UUID? = null
    private var lastKnownTargetPos: BlockPos? = null
    private var ticksSinceLastSeen = 0

    // ─── Target movement history for prediction ─────────────────────────
    private val targetPosHistory = ArrayDeque<Vec3>(PREDICTION_SAMPLES)
    private var predictionRecordTick = 0

    var difficultyMultiplier = 1.0f

    // ═══════════════════════════════════════════════════════════════════════
    // BLOCK INTERACTION
    // ═══════════════════════════════════════════════════════════════════════

    private var blockPlaceCooldown = 0
    private var blockBreakCooldown = 0
    private var blockBreakProgress = 0
    private var currentBreakingPos: BlockPos? = null

    private val baseBlockPlaceCooldown get() = (8 / difficultyMultiplier).toInt().coerceAtLeast(2)
    private val baseBlockBreakCooldown get() = (12 / difficultyMultiplier).toInt().coerceAtLeast(2)

    private val blockInventory = mutableMapOf<BlockState, Int>()
    private val maxBlocks = 128

    // ═══════════════════════════════════════════════════════════════════════
    // RESOURCE ECONOMY
    // ═══════════════════════════════════════════════════════════════════════

    private var woodLogs = 0
    private var woodPlanks = 0
    private var sticks = 0
    private var cobblestoneCount = 0
    private var dirtCount = 0
    private var coalCount = 0
    private var ironOreCount = 0
    private var ironIngotCount = 0

    private var hasCraftingTable = false
    private var hasFurnace = false
    private var hasWoodAxe = false
    private var hasStonePickaxe = false
    private var hasIronPickaxe = false
    private var hasStoneSword = false
    private var hasIronSword = false

    private var smeltProgress = 0
    private var gatherTargetPos: BlockPos? = null
    private var gatherRetargetCooldown = 0
    private var gatherFocusTicks = 0
    private var bedScanCooldown = 0
    private var waterSearchCooldown = 0
    private var homeSpawnPos: BlockPos? = null
    private var bedSpawnPos: BlockPos? = null

    // ═══════════════════════════════════════════════════════════════════════
    // SPECIAL ABILITIES
    // ═══════════════════════════════════════════════════════════════════════

    private var sprintBurstCooldown = 0
    private var leapCooldown = 0
    private var rageModeTimer = 0
    private var rageCooldown = 0

    // ═══════════════════════════════════════════════════════════════════════
    // COMBAT
    // ═══════════════════════════════════════════════════════════════════════

    private var attackCooldown = 0
    private var comboCount = 0
    private var lastAttackTick = 0L
    /** Combat strafe direction. 1 = clockwise, -1 = CCW */
    private var strafeDir = 1
    private var strafeChangeTick = 0L

    // ═══════════════════════════════════════════════════════════════════════
    // STUCK DETECTION
    // ═══════════════════════════════════════════════════════════════════════

    private var lastNavPos = Vec3.ZERO
    private var stuckTicks = 0
    private var hardStuckTicks = 0
    private var teleportAttemptCooldown = 0

    // ═══════════════════════════════════════════════════════════════════════
    // GOAL REGISTRATION — ALL goals now properly registered
    // ═══════════════════════════════════════════════════════════════════════

    override fun registerGoals() {
        goalSelector.addGoal(0, HunterFreezeGoal(this))
        goalSelector.addGoal(1, HunterSmartCombatGoal(this))
        goalSelector.addGoal(2, HunterInterceptGoal(this))
        goalSelector.addGoal(3, HunterBuildGoal(this))
        goalSelector.addGoal(4, HunterBreakGoal(this))
        goalSelector.addGoal(5, HunterChaseGoal(this))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PATHFINDING — hazard avoidance
    // ═══════════════════════════════════════════════════════════════════════

    override fun createNavigation(level: Level): PathNavigation {
        val nav = super.createNavigation(level)
        setPathfindingMalus(PathType.LAVA, -1.0f)
        setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0f)
        setPathfindingMalus(PathType.DANGER_FIRE, 12.0f)
        setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0f)
        setPathfindingMalus(PathType.DANGER_OTHER, 8.0f)
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0f)
        // Hunter doesn't mind water as much — can pursue through it
        setPathfindingMalus(PathType.WATER, 2.0f)
        return nav
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════

    override fun finalizeSpawn(
        accessor: ServerLevelAccessor,
        difficulty: DifficultyInstance,
        reason: EntitySpawnReason,
        spawnData: SpawnGroupData?
    ): SpawnGroupData? {
        val data = super.finalizeSpawn(accessor, difficulty, reason, spawnData)
        equipHunter()
        initializeBlockInventory()
        return data
    }

    private fun equipHunter() {
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
        setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET))
        setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.IRON_CHESTPLATE))
        setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.IRON_LEGGINGS))
        setItemSlot(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS))
    }

    private fun initializeBlockInventory() {
        blockInventory.clear()
        blockInventory[Blocks.COBBLESTONE.defaultBlockState()] = 64
        blockInventory[Blocks.DIRT.defaultBlockState()] = 32
        blockInventory[Blocks.OAK_PLANKS.defaultBlockState()] = 32
        cobblestoneCount = 64
        dirtCount = 32
        woodPlanks = 32
        sticks = 8
        woodLogs = 0
        coalCount = 0
        ironOreCount = 0
        ironIngotCount = 0
        hasCraftingTable = true
        hasFurnace = true
        hasWoodAxe = true
        hasStonePickaxe = true
        hasIronPickaxe = false
        hasStoneSword = false
        hasIronSword = true
        smeltProgress = 0
        gatherTargetPos = null
        gatherRetargetCooldown = 0
        gatherFocusTicks = 0
        bedScanCooldown = 0
        waterSearchCooldown = 0
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN AI TICK — lightweight brain; movement handled by Goals
    // ═══════════════════════════════════════════════════════════════════════

    override fun aiStep() {
        super.aiStep()
        if (level().isClientSide) return
        val serverLevel = level() as? ServerLevel ?: return

        if (currentState == HunterState.FROZEN) { navigation.stop(); return }

        updateCooldowns()
        tickRageMode()
        updateTarget(serverLevel)
        recordTargetPosition()
        tickStuckDetection(serverLevel)

        // Background maintenance (doesn't move the hunter)
        updateBedRespawnAnchor(serverLevel)
        handleFallSurvival(serverLevel)
        tickCraftingEconomy()
        handleSpecialAbilities(serverLevel)

        // Particles
        if (currentState == HunterState.HUNTING && tickCount % 10 == 0) {
            serverLevel.sendParticles(
                if (rageModeTimer > 0) ParticleTypes.FLAME else ParticleTypes.SMOKE,
                x, y + 0.5, z, 3, 0.2, 0.2, 0.2, 0.01
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
        if (gatherRetargetCooldown > 0) gatherRetargetCooldown--
        if (bedScanCooldown > 0) bedScanCooldown--
        if (waterSearchCooldown > 0) waterSearchCooldown--
        if (teleportAttemptCooldown > 0) teleportAttemptCooldown--
    }

    private fun tickRageMode() {
        if (rageModeTimer > 0) {
            rageModeTimer--
            if (rageModeTimer <= 0) exitRageMode()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TARGET MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    private fun updateTarget(serverLevel: ServerLevel) {
        val game = gameId?.let { ManhuntManager.getGame(it) }
        if (game == null || currentState == HunterState.IDLE) {
            target = null; return
        }

        val speedrunners = game.getAliveSpeedrunners()
            .mapNotNull { serverLevel.server?.playerList?.getPlayer(it) }
            .filter { it.isAlive && !it.isSpectator }

        if (speedrunners.isEmpty()) {
            target = null; currentTargetUUID = null; return
        }

        // Prefer current target unless someone is MUCH closer (hysteresis prevents jitter)
        val currentTarget = currentTargetUUID?.let { uuid -> speedrunners.find { it.uuid == uuid } }
        val newTarget = if (currentTarget != null && currentTarget.isAlive) {
            val currentDist = distanceToSqr(currentTarget)
            val closer = speedrunners.filter { it != currentTarget }.minByOrNull { distanceToSqr(it) }
            if (closer != null && distanceToSqr(closer) < currentDist * 0.4) closer else currentTarget
        } else {
            speedrunners.minByOrNull { distanceToSqr(it) }
        }

        if (newTarget != null) {
            target = newTarget
            currentTargetUUID = newTarget.uuid
            lastKnownTargetPos = newTarget.blockPosition()
            ticksSinceLastSeen = 0
            if (currentState == HunterState.IDLE) setState(HunterState.HUNTING)
        } else {
            ticksSinceLastSeen++
        }
    }

    // ─── Movement prediction ────────────────────────────────────────────

    private fun recordTargetPosition() {
        if (++predictionRecordTick < 4) return
        predictionRecordTick = 0
        val t = target ?: return
        if (targetPosHistory.size >= PREDICTION_SAMPLES) targetPosHistory.removeFirst()
        targetPosHistory.addLast(t.position())
    }

    /**
     * Predicts where the target will be [ticksAhead] ticks from now based on
     * recent movement history. Falls back to current position.
     */
    fun predictTargetPosition(ticksAhead: Int): Vec3 {
        val t = target ?: return position()
        if (targetPosHistory.size < 3) return t.position()

        // Average velocity from last N samples
        var vx = 0.0; var vy = 0.0; var vz = 0.0
        for (i in 1 until targetPosHistory.size) {
            val prev = targetPosHistory[i - 1]
            val cur = targetPosHistory[i]
            vx += cur.x - prev.x
            vy += cur.y - prev.y
            vz += cur.z - prev.z
        }
        val n = (targetPosHistory.size - 1).toDouble()
        vx /= n; vy /= n; vz /= n

        // Scale from sample interval (4 ticks) to requested horizon
        val scale = ticksAhead / 4.0
        return Vec3(
            t.x + vx * scale,
            t.y + vy * scale,
            t.z + vz * scale
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STUCK DETECTION & EMERGENCY TELEPORT
    // ═══════════════════════════════════════════════════════════════════════

    private fun tickStuckDetection(serverLevel: ServerLevel) {
        val pos = position()
        if (pos.distanceToSqr(lastNavPos) < 0.02 && !navigation.isDone) {
            stuckTicks++
            if (stuckTicks > 40) hardStuckTicks++
        } else {
            stuckTicks = 0
            hardStuckTicks = 0
        }
        lastNavPos = pos

        // 3 seconds stuck → try breaking blocks aggressively
        if (hardStuckTicks > 60) {
            forceBreakNearestObstacle()
            hardStuckTicks = 40 // reset partially, keep pressure
        }

        // 6+ seconds totally stuck with a target → emergency teleport
        if (stuckTicks > 120 && teleportAttemptCooldown <= 0) {
            attemptEmergencyTeleport(serverLevel)
            teleportAttemptCooldown = 100
        }
    }

    private fun forceBreakNearestObstacle() {
        val dirToTarget = if (target != null) {
            Vec3(target!!.x - x, target!!.y - y, target!!.z - z).normalize()
        } else {
            lookAngle
        }
        val positions = listOf(
            blockPosition().offset(dirToTarget.x.toInt().coerceIn(-1, 1), 0, dirToTarget.z.toInt().coerceIn(-1, 1)),
            blockPosition().offset(dirToTarget.x.toInt().coerceIn(-1, 1), 1, dirToTarget.z.toInt().coerceIn(-1, 1)),
            blockPosition().above(),
            blockPosition().relative(direction),
            blockPosition().relative(direction).above()
        )
        for (pos in positions) {
            val state = level().getBlockState(pos)
            if (!state.isAir && state.getDestroySpeed(level(), pos) >= 0) {
                tryBreakBlock(pos); return
            }
        }
    }

    private fun attemptEmergencyTeleport(serverLevel: ServerLevel) {
        val t = target ?: return
        val tp = findSafePosNear(t.blockPosition(), serverLevel) ?: return
        teleportTo(tp.x + 0.5, tp.y.toDouble(), tp.z + 0.5)
        navigation.stop()
        stuckTicks = 0; hardStuckTicks = 0
        broadcastToNearbyPlayers(serverLevel, "§c§l⚡ The Hunter warps closer!")
    }

    private fun findSafePosNear(origin: BlockPos, level: ServerLevel): BlockPos? {
        for (r in 2..6) for (dx in -r..r) for (dz in -r..r) {
            if (abs(dx) != r && abs(dz) != r) continue
            for (dy in -2..3) {
                val pos = origin.offset(dx, dy, dz)
                if (level.getBlockState(pos.below()).isSolid &&
                    level.getBlockState(pos).isAir &&
                    level.getBlockState(pos.above()).isAir
                ) return pos
            }
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BLOCK INTERACTION
    // ═══════════════════════════════════════════════════════════════════════

    fun tryPlaceBlock(pos: BlockPos): Boolean {
        if (blockPlaceCooldown > 0 || level().isClientSide) return false
        val serverLevel = level() as? ServerLevel ?: return false
        if (!serverLevel.getBlockState(pos).isAir) return false
        val blockToPlace = blockInventory.entries.firstOrNull { it.value > 0 } ?: return false
        val hasSupport = Direction.entries.any { serverLevel.getBlockState(pos.relative(it)).isSolid }
        if (!hasSupport) return false
        serverLevel.setBlock(pos, blockToPlace.key, 3)
        blockInventory[blockToPlace.key] = blockToPlace.value - 1
        serverLevel.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f)
        blockPlaceCooldown = baseBlockPlaceCooldown
        swing(InteractionHand.MAIN_HAND)
        return true
    }

    fun tryBreakBlock(pos: BlockPos): Boolean {
        if (blockBreakCooldown > 0 && currentBreakingPos != pos) return false
        if (level().isClientSide) return false
        val serverLevel = level() as? ServerLevel ?: return false
        val blockState = serverLevel.getBlockState(pos)
        if (blockState.isAir || blockState.getDestroySpeed(serverLevel, pos) < 0) return false
        if (blockState.`is`(BlockTags.FEATURES_CANNOT_REPLACE)) return false

        equipToolForBlock(blockState)

        if (currentBreakingPos != pos) { currentBreakingPos = pos; blockBreakProgress = 0 }

        val hardness = blockState.getDestroySpeed(serverLevel, pos)
        val base = ((hardness * 40) / difficultyMultiplier.coerceAtLeast(0.8f)).toInt().coerceAtLeast(8)
        val toolMul = when {
            blockState.`is`(BlockTags.LOGS) && hasAnyAxe() -> 0.7
            blockState.`is`(BlockTags.MINEABLE_WITH_PICKAXE) && hasAnyPickaxe() -> 0.65
            else -> 1.5
        }
        val breakTime = (base * toolMul).toInt().coerceAtLeast(6)

        blockBreakProgress++
        if (blockBreakProgress % 4 == 0) {
            serverLevel.sendParticles(ParticleTypes.CRIT, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 4, 0.3, 0.3, 0.3, 0.1)
            swing(InteractionHand.MAIN_HAND)
        }

        if (blockBreakProgress >= breakTime) {
            serverLevel.destroyBlock(pos, false)
            collectResourceFromBrokenBlock(blockState)
            currentBreakingPos = null; blockBreakProgress = 0
            blockBreakCooldown = baseBlockBreakCooldown + 4
            return true
        }
        return false
    }

    private fun getTotalBlocks(): Int = blockInventory.values.sum()

    // ═══════════════════════════════════════════════════════════════════════
    // RESOURCE ECONOMY / CRAFTING
    // ═══════════════════════════════════════════════════════════════════════

    private fun tickCraftingEconomy() {
        convertLogsToPlanksAndSticks()
        craftProgression()
        smeltProgression()
    }

    private fun collectResourceFromBrokenBlock(state: BlockState) {
        when {
            state.`is`(BlockTags.LOGS) -> woodLogs += 1
            state.`is`(Blocks.IRON_ORE) || state.`is`(Blocks.DEEPSLATE_IRON_ORE) -> ironOreCount += 1
            state.`is`(Blocks.COAL_ORE) || state.`is`(Blocks.DEEPSLATE_COAL_ORE) -> coalCount += 1
            state.`is`(Blocks.DIRT) || state.`is`(Blocks.GRASS_BLOCK) || state.`is`(Blocks.COARSE_DIRT) -> {
                dirtCount += 1; addBuildingBlock(Blocks.DIRT.defaultBlockState(), 1)
            }
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) -> {
                cobblestoneCount += 1; addBuildingBlock(Blocks.COBBLESTONE.defaultBlockState(), 1)
            }
        }
    }

    private fun convertLogsToPlanksAndSticks() {
        if (woodLogs > 0) { woodPlanks += woodLogs * 4; woodLogs = 0 }
        while (woodPlanks >= 2 && sticks < 16) { woodPlanks -= 2; sticks += 4 }
    }

    private fun craftProgression() {
        if (!hasCraftingTable && woodPlanks >= 4) { woodPlanks -= 4; hasCraftingTable = true }
        if (!hasWoodAxe && hasCraftingTable && woodPlanks >= 3 && sticks >= 2) { woodPlanks -= 3; sticks -= 2; hasWoodAxe = true }
        if (!hasStonePickaxe && hasCraftingTable && cobblestoneCount >= 3 && sticks >= 2) { cobblestoneCount -= 3; sticks -= 2; hasStonePickaxe = true }
        if (!hasStoneSword && hasCraftingTable && cobblestoneCount >= 2 && sticks >= 1) { cobblestoneCount -= 2; sticks -= 1; hasStoneSword = true }
        if (!hasFurnace && hasCraftingTable && cobblestoneCount >= 8) { cobblestoneCount -= 8; hasFurnace = true }
        if (!hasIronPickaxe && hasCraftingTable && ironIngotCount >= 3 && sticks >= 2) { ironIngotCount -= 3; sticks -= 2; hasIronPickaxe = true }
        if (!hasIronSword && hasCraftingTable && ironIngotCount >= 2 && sticks >= 1) { ironIngotCount -= 2; sticks -= 1; hasIronSword = true }
        refillBuildingBlocks()
        equipCombatWeapon()
    }

    private fun smeltProgression() {
        if (!hasFurnace || ironOreCount <= 0) return
        val hasFuel = coalCount > 0 || woodPlanks >= 2
        if (!hasFuel) return
        smeltProgress++
        val smeltNeeded = (90 / difficultyMultiplier).toInt().coerceAtLeast(25)
        if (smeltProgress < smeltNeeded) return
        smeltProgress = 0
        ironOreCount -= 1
        if (coalCount > 0) coalCount -= 1 else woodPlanks -= 2
        ironIngotCount += 1
    }

    private fun refillBuildingBlocks() {
        fun refill(state: BlockState, count: () -> Int, reduce: (Int) -> Unit, cap: Int) {
            val current = blockInventory[state] ?: 0
            if (count() > 0 && current < cap) {
                val toMove = minOf(count(), cap - current)
                reduce(toMove)
                addBuildingBlock(state, toMove)
            }
        }
        refill(Blocks.COBBLESTONE.defaultBlockState(), { cobblestoneCount }, { cobblestoneCount -= it }, 64)
        refill(Blocks.DIRT.defaultBlockState(), { dirtCount }, { dirtCount -= it }, 48)
        refill(Blocks.OAK_PLANKS.defaultBlockState(), { woodPlanks }, { woodPlanks -= it }, 48)
    }

    private fun addBuildingBlock(state: BlockState, count: Int) {
        val current = blockInventory[state] ?: 0
        blockInventory[state] = (current + count).coerceAtMost(maxBlocks)
    }

    private fun canMineIron(): Boolean = hasStonePickaxe || hasIronPickaxe
    private fun hasAnyAxe(): Boolean = hasWoodAxe
    private fun hasAnyPickaxe(): Boolean = hasStonePickaxe || hasIronPickaxe

    private fun equipCombatWeapon() {
        if (rageModeTimer > 0) return // rage mode uses diamond sword
        val item = when {
            hasIronSword -> Items.IRON_SWORD
            hasStoneSword -> Items.STONE_SWORD
            else -> Items.WOODEN_SWORD
        }
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(item))
    }

    private fun equipToolForBlock(state: BlockState) {
        val toolItem: Item = when {
            state.`is`(BlockTags.LOGS) && hasAnyAxe() -> Items.WOODEN_AXE
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) && hasIronPickaxe -> Items.IRON_PICKAXE
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) && hasStonePickaxe -> Items.STONE_PICKAXE
            else -> { equipCombatWeapon(); return }
        }
        if (mainHandItem.item != toolItem) setItemSlot(EquipmentSlot.MAINHAND, ItemStack(toolItem))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BUILDING HELPERS (used by Goals)
    // ═══════════════════════════════════════════════════════════════════════

    fun buildTowardsTarget(): Boolean {
        val targetEntity = target ?: return false
        val targetPos = targetEntity.blockPosition()
        if (targetPos.y <= blockPosition().y + 2) return false
        if (onGround()) { jumpFromGround(); return false }
        if (deltaMovement.y > 0.05) {
            val placePos = blockPosition().below()
            if (level().getBlockState(placePos).isAir) return tryPlaceBlock(placePos)
        }
        return false
    }

    fun buildBridgeTowards(targetPos: BlockPos): Boolean {
        val myPos = blockPosition()
        val dx = (targetPos.x - myPos.x).coerceIn(-1, 1)
        val dz = (targetPos.z - myPos.z).coerceIn(-1, 1)
        val bridgePos = myPos.offset(dx, -1, dz)
        if (level().getBlockState(bridgePos).isAir) return tryPlaceBlock(bridgePos)
        return false
    }

    /** Wall off an escape route: places blocks on both sides of hunter's path toward target. */
    fun tryWallOff(): Boolean {
        val t = target ?: return false
        val toTarget = Vec3(t.x - x, 0.0, t.z - z).normalize()
        val perp = Vec3(-toTarget.z, 0.0, toTarget.x)
        val leftPos = blockPosition().offset(perp.x.toInt(), 0, perp.z.toInt())
        val rightPos = blockPosition().offset((-perp.x).toInt(), 0, (-perp.z).toInt())
        if (level().getBlockState(leftPos).isAir && tryPlaceBlock(leftPos)) return true
        if (level().getBlockState(rightPos).isAir && tryPlaceBlock(rightPos)) return true
        return false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADVANCED SURVIVAL
    // ═══════════════════════════════════════════════════════════════════════

    private fun updateBedRespawnAnchor(serverLevel: ServerLevel) {
        if (bedScanCooldown > 0) return
        bedScanCooldown = 20
        val origin = blockPosition()
        for (dx in -3..3) for (dy in -2..2) for (dz in -3..3) {
            val pos = origin.offset(dx, dy, dz)
            if (serverLevel.getBlockState(pos).`is`(BlockTags.BEDS)) { bedSpawnPos = pos; return }
        }
    }

    private fun handleFallSurvival(serverLevel: ServerLevel) {
        if (onGround() || fallDistance < 3.5f) return
        val water = if (waterSearchCooldown <= 0) {
            waterSearchCooldown = 6
            findNearbyWaterLanding(serverLevel, 6, 12)
        } else null
        if (water != null) {
            val target = Vec3(water.x + 0.5, y, water.z + 0.5)
            val dir = target.subtract(position()).normalize()
            val steer = 0.35 + difficultyMultiplier * 0.05
            deltaMovement = Vec3(dir.x * steer, deltaMovement.y, dir.z * steer)
            hurtMarked = true
            return
        }
        if (fallDistance > 5.0f) {
            val below = blockPosition().below()
            if (level().getBlockState(below).isAir) tryPlaceBlock(below)
        }
    }

    private fun findNearbyWaterLanding(serverLevel: ServerLevel, radius: Int, depth: Int): BlockPos? {
        val origin = blockPosition()
        var best: BlockPos? = null; var bestDist = Double.MAX_VALUE
        for (dx in -radius..radius) for (dz in -radius..radius) for (dy in 0..depth) {
            val pos = origin.offset(dx, -dy, dz)
            if (!serverLevel.getBlockState(pos).fluidState.`is`(Fluids.WATER)) continue
            val dist = origin.distSqr(pos).toDouble()
            if (dist < bestDist) { bestDist = dist; best = pos }
        }
        return best
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SPECIAL ABILITIES
    // ═══════════════════════════════════════════════════════════════════════

    private fun handleSpecialAbilities(serverLevel: ServerLevel) {
        val targetEntity = target as? Player ?: return
        val dist = distanceTo(targetEntity)

        // Sprint Burst — medium range
        if (dist in 10.0..30.0 && sprintBurstCooldown <= 0 &&
            (currentState == HunterState.HUNTING || currentState == HunterState.RAGING)
        ) {
            activateSprintBurst(serverLevel)
        }

        // Leap — close range with LOS
        if (dist in 4.0..8.0 && leapCooldown <= 0 && hasLineOfSight(targetEntity) && onGround()) {
            performLeap(serverLevel, targetEntity)
        }

        // Rage Mode — low HP or long chase
        if (rageCooldown <= 0 && rageModeTimer <= 0) {
            if (health < maxHealth * 0.3f || ticksSinceLastSeen > 500) {
                activateRageMode(serverLevel)
            }
        }
    }

    private fun activateSprintBurst(serverLevel: ServerLevel) {
        addEffect(MobEffectInstance(MobEffects.SPEED, 100, 1, false, true))
        sprintBurstCooldown = (400 / difficultyMultiplier).toInt()
        serverLevel.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 10, 0.3, 0.2, 0.3, 0.1)
        serverLevel.playSound(null, blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.HOSTILE, 0.8f, 1.2f)
        broadcastToNearbyPlayers(serverLevel, "§c§l⚡ The Hunter activates Sprint Burst!")
    }

    private fun performLeap(serverLevel: ServerLevel, targetEntity: Entity) {
        val direction = Vec3(targetEntity.x - x, (targetEntity.y - y) + 0.5, targetEntity.z - z).normalize()
        val leapStrength = 1.2 * difficultyMultiplier
        deltaMovement = Vec3(direction.x * leapStrength, 0.6, direction.z * leapStrength)
        hurtMarked = true
        leapCooldown = (200 / difficultyMultiplier).toInt()
        serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y + 1.0, z, 5, 0.5, 0.3, 0.5, 0.0)
        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 0.6f, 1.5f)
    }

    private fun activateRageMode(serverLevel: ServerLevel) {
        rageModeTimer = (400 * difficultyMultiplier).toInt()
        rageCooldown = 2400
        addEffect(MobEffectInstance(MobEffects.SPEED, rageModeTimer, 2, false, true))
        addEffect(MobEffectInstance(MobEffects.STRENGTH, rageModeTimer, 1, false, true))
        addEffect(MobEffectInstance(MobEffects.REGENERATION, rageModeTimer, 0, false, true))
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.DIAMOND_SWORD))
        serverLevel.sendParticles(ParticleTypes.FLAME, x, y + 1.0, z, 30, 0.5, 1.0, 0.5, 0.1)
        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.0f, 0.8f)
        broadcastToNearbyPlayers(serverLevel, "§4§l🔥 THE HUNTER HAS ENTERED RAGE MODE! 🔥")
        currentState = HunterState.RAGING
    }

    private fun exitRageMode() {
        equipCombatWeapon()
        if (currentState == HunterState.RAGING) currentState = HunterState.HUNTING
        val serverLevel = level() as? ServerLevel ?: return
        broadcastToNearbyPlayers(serverLevel, "§e⚡ The Hunter's rage subsides...")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMBAT
    // ═══════════════════════════════════════════════════════════════════════

    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        if (attackCooldown > 0) return false

        var damage = getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat()
        if (rageModeTimer > 0) damage *= 1.5f
        damage *= difficultyMultiplier

        // Combo system
        val currentTick = serverLevel.gameTime
        if (currentTick - lastAttackTick < 40) {
            comboCount++
            damage += comboCount * 0.5f
            if (comboCount >= 3) {
                // Knockback finisher — knock target toward nearest hazard or wall
                val knockDir = calculateSmartKnockback(target)
                target.deltaMovement = target.deltaMovement.add(knockDir)
                serverLevel.sendParticles(ParticleTypes.CRIT, target.x, target.y + 1.0, target.z, 10, 0.3, 0.3, 0.3, 0.2)
                comboCount = 0
            }
        } else {
            comboCount = 1
        }

        lastAttackTick = currentTick
        attackCooldown = (15 / difficultyMultiplier).toInt().coerceAtLeast(5)
        target.invulnerableTime = 0
        target.hurt(serverLevel.damageSources().mobAttack(this), damage)
        swing(InteractionHand.MAIN_HAND)
        serverLevel.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0f, 1.0f)
        return true
    }

    /** Try to knock the target into lava/fire or off a cliff if possible. */
    private fun calculateSmartKnockback(target: Entity): Vec3 {
        val serverLevel = level() as? ServerLevel ?: return Vec3(target.x - x, 0.5, target.z - z).normalize().scale(1.5)
        val targetPos = target.blockPosition()

        // Scan 8 directions for hazards
        var bestDir: Vec3? = null
        var bestScore = -1.0

        for (dir in Direction.Plane.HORIZONTAL) {
            val checkPos = targetPos.relative(dir, 2)
            val state = serverLevel.getBlockState(checkPos)
            val belowState = serverLevel.getBlockState(checkPos.below())
            var score = 0.0
            if (state.fluidState.`is`(Fluids.LAVA) || belowState.fluidState.`is`(Fluids.LAVA)) score += 100.0
            if (belowState.isAir) score += 50.0  // cliff
            if (!state.isAir) score -= 20.0       // wall blocks knockback
            if (score > bestScore) {
                bestScore = score
                bestDir = Vec3(dir.stepX.toDouble(), 0.5, dir.stepZ.toDouble()).normalize().scale(1.8)
            }
        }

        return bestDir ?: Vec3(target.x - x, 0.5, target.z - z).normalize().scale(1.5)
    }

    /** Get the hunter's current strafe direction; periodically randomized. */
    fun getStrafeDir(): Int {
        if (tickCount.toLong() - strafeChangeTick > 25 + random.nextInt(25)) {
            strafeDir = -strafeDir
            strafeChangeTick = tickCount.toLong()
        }
        return strafeDir
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT (public API)
    // ═══════════════════════════════════════════════════════════════════════

    fun setState(newState: HunterState) {
        val oldState = currentState
        currentState = newState
        val serverLevel = level() as? ServerLevel ?: return
        when (newState) {
            HunterState.HUNTING -> {
                if (oldState == HunterState.IDLE || oldState == HunterState.FROZEN)
                    broadcastToNearbyPlayers(serverLevel, "§c§l🎯 The Hunt begins!")
            }
            HunterState.FROZEN -> {
                navigation.stop()
                broadcastToNearbyPlayers(serverLevel, "§e⏸ The Hunter has been deactivated.")
            }
            else -> {}
        }
    }

    fun freeze() { setState(HunterState.FROZEN) }

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

    fun setPrimarySpawn(pos: BlockPos) { homeSpawnPos = pos }

    fun getPreferredRespawnPos(serverLevel: ServerLevel): BlockPos {
        val bed = bedSpawnPos
        if (bed != null && serverLevel.getBlockState(bed).`is`(BlockTags.BEDS)) return bed.above()
        return homeSpawnPos ?: blockPosition()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════

    private fun broadcastToNearbyPlayers(serverLevel: ServerLevel, message: String) {
        serverLevel.players().filter { distanceTo(it) < 200.0 }.forEach {
            it.sendSystemMessage(Component.literal(message))
        }
    }

    fun giveBlocks(count: Int) {
        val cobble = Blocks.COBBLESTONE.defaultBlockState()
        blockInventory[cobble] = (blockInventory[cobble] ?: 0) + count
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DAMAGE HANDLING
    // ═══════════════════════════════════════════════════════════════════════

    fun onHurt(source: DamageSource, amount: Float) {
        if (health < maxHealth * 0.3f && rageCooldown <= 0 && rageModeTimer <= 0) {
            val serverLevel = level() as? ServerLevel
            if (serverLevel != null) activateRageMode(serverLevel)
        }
    }

    override fun isInvulnerableTo(level: ServerLevel, source: DamageSource): Boolean =
        super.isInvulnerableTo(level, source)

    override fun knockback(strength: Double, x: Double, z: Double) {
        super.knockback(strength / (difficultyMultiplier + 0.5), x, z)
    }

    override fun isPushable(): Boolean = false
    override fun checkDespawn() { setPersistenceRequired() }
    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false
    override fun requiresCustomPersistence(): Boolean = true

    // ═══════════════════════════════════════════════════════════════════════
    // COMPANION OBJECT
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        private const val PREDICTION_SAMPLES = 10

        private val activeHunters = ConcurrentHashMap<String, Int>()

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
            hunter.setPrimarySpawn(pos)
            hunter.setPersistenceRequired()
            hunter.getAttribute(Attributes.MAX_HEALTH)?.baseValue = 60.0 * difficulty
            hunter.health = hunter.maxHealth
            hunter.getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue = 0.32 + (difficulty * 0.03)
            hunter.getAttribute(Attributes.ATTACK_DAMAGE)?.baseValue = 4.0 + (difficulty * 2.0)
            serverLevel.addFreshEntity(hunter)
            activeHunters[gameId] = hunter.id
            return hunter
        }

        fun getHunterForGame(gameId: String): HunterEntity? {
            activeHunters[gameId] ?: return null
            return null
        }

        fun removeHunterForGame(gameId: String) { activeHunters.remove(gameId) }

        private fun findGroundLevel(level: ServerLevel, pos: BlockPos): Int {
            var checkPos = pos.atY(level.maxY - 1)
            while (checkPos.y > level.minY) {
                if (level.getBlockState(checkPos).isAir && level.getBlockState(checkPos.below()).isSolid) return checkPos.y
                checkPos = checkPos.below()
            }
            return level.minY
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: FREEZE (highest priority — blocks everything when frozen)
// ═══════════════════════════════════════════════════════════════════════════════

private class HunterFreezeGoal(private val hunter: HunterEntity) : Goal() {
    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP) }
    override fun canUse(): Boolean = hunter.currentState == HunterEntity.HunterState.FROZEN
    override fun start() { hunter.navigation.stop() }
    override fun tick() { hunter.navigation.stop() }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: SMART MELEE COMBAT
//   • Strafes in melee range to dodge hits
//   • Critical-hit timing (jump → attack while falling)
//   • Sprint attacks from medium range
//   • Continues attacking while circling
// ═══════════════════════════════════════════════════════════════════════════════

private class HunterSmartCombatGoal(private val hunter: HunterEntity) : Goal() {
    private var attackCd = 0
    private var pathRecalc = 0

    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK) }

    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        val t = hunter.target ?: return false
        return hunter.distanceTo(t) < 5.0f
    }

    override fun canContinueToUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        val t = hunter.target ?: return false
        return t.isAlive && hunter.distanceTo(t) < 6.0f
    }

    override fun start() {
        attackCd = 0; pathRecalc = 0
        hunter.navigation.stop()
    }

    override fun tick() {
        val target = hunter.target ?: return
        val dist = hunter.distanceTo(target)

        hunter.lookControl.setLookAt(target, 30f, 30f)
        if (attackCd > 0) attackCd--

        if (dist > 3.5f) {
            // Approach quickly
            if (--pathRecalc <= 0) {
                val speed = if (hunter.currentState == HunterEntity.HunterState.RAGING) 1.5 else 1.3
                hunter.navigation.moveTo(target, speed * hunter.difficultyMultiplier)
                pathRecalc = 6
            }
            return
        }

        // ── In melee range: strafe + attack ────────────────────────────
        hunter.navigation.stop()
        val toTarget = Vec3(target.x - hunter.x, 0.0, target.z - hunter.z).normalize()
        val perp = Vec3(-toTarget.z, 0.0, toTarget.x)
        val strafeDir = hunter.getStrafeDir()
        val strafe = perp.scale(strafeDir * 0.4).add(toTarget.scale(-0.1))

        hunter.moveControl.setWantedPosition(
            hunter.x + strafe.x * 3.0, hunter.y, hunter.z + strafe.z * 3.0, 1.1
        )

        // Attack with crit timing
        if (attackCd <= 0 && dist < 3.0f) {
            if (hunter.onGround() && hunter.random.nextFloat() < 0.5f) {
                hunter.jumpFromGround() // jump for critical hit
            }
            val serverLevel = hunter.level() as? ServerLevel
            if (serverLevel != null) {
                hunter.doHurtTarget(serverLevel, target)
                attackCd = (12 / hunter.difficultyMultiplier).toInt().coerceAtLeast(4)
            }
        }
    }

    override fun stop() { hunter.navigation.stop() }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: INTERCEPT (prediction-based chasing)
//   • Uses target movement history to predict future position
//   • Paths toward intercept point rather than current position
//   • Anti-cheese: detects towering and builds up; detects walling and breaks
// ═══════════════════════════════════════════════════════════════════════════════

private class HunterInterceptGoal(private val hunter: HunterEntity) : Goal() {
    private var pathRecalc = 0
    private var lastTargetY = 0.0

    init { flags = EnumSet.of(Flag.MOVE) }

    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        val t = hunter.target ?: return false
        return hunter.distanceTo(t) >= 5.0f // Hand off to combat goal when close
    }

    override fun canContinueToUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        val t = hunter.target ?: return false
        return t.isAlive && hunter.distanceTo(t) >= 4.0f
    }

    override fun tick() {
        val target = hunter.target ?: return
        val dist = hunter.distanceTo(target)

        if (--pathRecalc > 0) return

        // ── Predict intercept point ─────────────────────────────────────
        val ticksAhead = when {
            dist > 40 -> 40
            dist > 20 -> 25
            else -> 15
        }
        val predicted = hunter.predictTargetPosition(ticksAhead)

        val speed = when {
            hunter.currentState == HunterEntity.HunterState.RAGING -> 1.55
            dist > 30 -> 1.4
            dist > 15 -> 1.25
            else -> 1.15
        } * hunter.difficultyMultiplier

        hunter.navigation.moveTo(predicted.x, predicted.y, predicted.z, speed.coerceIn(0.9, 2.0))

        // ── Anti-cheese: target towering up → build up ──────────────────
        val heightDiff = target.y - hunter.y
        if (heightDiff > 3.0) {
            hunter.buildTowardsTarget()
        }

        // ── Anti-cheese: gap over void/lava → bridge ────────────────────
        val ahead = hunter.blockPosition().relative(hunter.direction)
        val belowAhead = ahead.below()
        if (hunter.level().getBlockState(belowAhead).isAir || hunter.level().getBlockState(belowAhead).fluidState.`is`(Fluids.LAVA)) {
            hunter.buildBridgeTowards(target.blockPosition())
        }

        // ── Can't see target → break obstacles ──────────────────────────
        if (!hunter.hasLineOfSight(target) && dist < 20f) {
            tryBreakToward(target)
        }

        // Recalc frequency scales with distance (close = more reactive)
        pathRecalc = when {
            dist > 40 -> 15
            dist > 20 -> 10
            dist > 10 -> 6
            else -> 4
        }

        lastTargetY = target.y
    }

    private fun tryBreakToward(target: Entity) {
        val dir = Vec3(target.x - hunter.x, target.y - hunter.y, target.z - hunter.z).normalize()
        val eyePos = hunter.blockPosition().above()
        val front = eyePos.offset(dir.x.toInt().coerceIn(-1, 1), 0, dir.z.toInt().coerceIn(-1, 1))
        if (!hunter.level().getBlockState(front).isAir) { hunter.tryBreakBlock(front); return }
        val frontLow = front.below()
        if (!hunter.level().getBlockState(frontLow).isAir) hunter.tryBreakBlock(frontLow)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: CHASE (fallback direct chase when intercept can't compute)
// ═══════════════════════════════════════════════════════════════════════════════

private class HunterChaseGoal(private val hunter: HunterEntity) : Goal() {
    private var pathRecalc = 0

    init { flags = EnumSet.of(Flag.MOVE) }

    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        return hunter.target != null && hunter.navigation.isDone
    }

    override fun tick() {
        val target = hunter.target ?: return
        if (--pathRecalc <= 0) {
            val speed = if (hunter.currentState == HunterEntity.HunterState.RAGING) 1.5 else 1.2
            hunter.navigation.moveTo(target, speed * hunter.difficultyMultiplier)
            pathRecalc = 12
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: BUILD UP (tower toward higher target)
// ═══════════════════════════════════════════════════════════════════════════════

private class HunterBuildGoal(private val hunter: HunterEntity) : Goal() {
    init { flags = EnumSet.of(Flag.MOVE, Flag.JUMP) }

    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        val target = hunter.target ?: return false
        return target.y - hunter.y > 3.0 && hunter.distanceTo(target) < 24f
    }

    override fun canContinueToUse(): Boolean {
        val target = hunter.target ?: return false
        return target.y - hunter.y > 1.0 && hunter.distanceTo(target) < 30f
    }

    override fun start() { hunter.setState(HunterEntity.HunterState.BUILDING) }

    override fun tick() { hunter.buildTowardsTarget() }

    override fun stop() {
        if (hunter.currentState == HunterEntity.HunterState.BUILDING) hunter.setState(HunterEntity.HunterState.HUNTING)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: BREAK OBSTACLES (aggressive stuck recovery)
// ═══════════════════════════════════════════════════════════════════════════════

private class HunterBreakGoal(private val hunter: HunterEntity) : Goal() {
    private var stuckTicks = 0
    private var lastPos: Vec3? = null

    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK) }

    override fun canUse(): Boolean {
        if (hunter.currentState == HunterEntity.HunterState.FROZEN) return false
        if (hunter.target == null) return false
        val currentPos = hunter.position()
        val last = lastPos
        stuckTicks = if (last != null && currentPos.distanceToSqr(last) < 0.02 && !hunter.navigation.isDone) stuckTicks + 1 else 0
        lastPos = currentPos
        return stuckTicks > 18 // ~0.9 seconds stuck
    }

    override fun start() { hunter.setState(HunterEntity.HunterState.BREAKING) }

    override fun tick() {
        val target = hunter.target ?: return
        val direction = Vec3(target.x - hunter.x, target.y - hunter.y, target.z - hunter.z).normalize()

        // Try to break a 2-high tunnel in the direction of the target
        val positions = listOf(
            hunter.blockPosition().offset(direction.x.toInt().coerceIn(-1, 1), 0, direction.z.toInt().coerceIn(-1, 1)),
            hunter.blockPosition().offset(direction.x.toInt().coerceIn(-1, 1), 1, direction.z.toInt().coerceIn(-1, 1)),
            hunter.blockPosition().above(),
            hunter.blockPosition().above().above()
        )

        for (pos in positions) {
            val level = hunter.level()
            if (!level.getBlockState(pos).isAir && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0) {
                hunter.tryBreakBlock(pos)
                hunter.lookControl.setLookAt(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                return
            }
        }
    }

    override fun stop() {
        stuckTicks = 0
        if (hunter.currentState == HunterEntity.HunterState.BREAKING) hunter.setState(HunterEntity.HunterState.HUNTING)
    }
}
