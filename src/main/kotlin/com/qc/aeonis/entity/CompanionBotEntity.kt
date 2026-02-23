package com.qc.aeonis.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║               COMPANION BOT — REMASTERED ADVANCED AI                       ║
 * ║                                                                            ║
 * ║  A player-owned combat companion with sophisticated AI featuring:          ║
 * ║  • Utility-scored threat assessment with memory                            ║
 * ║  • Strafing melee combat with critical-hit timing                          ║
 * ║  • Predictive owner following with formation positioning                   ║
 * ║  • Intelligent creeper, skeleton, and boss evasion                         ║
 * ║  • Multi-layered stuck detection and recovery                              ║
 * ║  • Environmental hazard avoidance via pathfinding malus                    ║
 * ║  • Resource gathering and auto-crafting when safe                          ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
class CompanionBotEntity(
    entityType: EntityType<out CompanionBotEntity>,
    level: Level
) : PathfinderMob(entityType, level) {

    // ═══════════════════════════════════════════════════════════════════════
    // STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════════

    enum class CompanionState {
        IDLE,
        FOLLOWING,
        DEFENDING,
        GATHERING,
        EVADING
    }

    /** Modes the Rhistel whistle can force. NONE = normal auto AI. */
    enum class ForcedMode {
        NONE,      // AUTO — default decision-making
        ATTACK,    // aggressively target nearest hostile mob
        FOLLOW,    // stick to owner, ignore combat
        RUN_AWAY   // flee from nearest hostile
    }

    var currentState = CompanionState.IDLE
        private set

    // ─── Forced mode (Rhistel whistle) ──────────────────────────────
    var forcedMode = ForcedMode.NONE
        private set
    private var forcedModeTicks = 0

    /**
     * Called by [com.qc.aeonis.item.RhistelItem] to override the AI.
     * [mode] NONE clears the override instantly.
     * Any other mode lasts [durationTicks] (default 2 min = 2 400 ticks).
     */
    fun setForcedMode(mode: ForcedMode, durationTicks: Int = 2400) {
        forcedMode = mode
        forcedModeTicks = if (mode == ForcedMode.NONE) 0 else durationTicks
        // Immediate side-effects:
        when (mode) {
            ForcedMode.FOLLOW, ForcedMode.RUN_AWAY -> {
                target = null
                retaliationTargetUuid = null
            }
            ForcedMode.ATTACK -> {
                retreatTicks = 0   // cancel any retreat so we engage
            }
            else -> {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMBAT / TARGETING
    // ═══════════════════════════════════════════════════════════════════════

    private var retaliationTargetUuid: UUID? = null
    private var ownerOfflineTicks = 0
    private var ownerDistressTicks = 0
    private var targetRefreshCooldown = 0

    /** Combat strafing direction: 1 = clockwise, -1 = counter-clockwise */
    private var strafeDirection = 1
    private var strafeChangeTick = 0L
    /** Ticks the bot has been continuously in combat */
    private var combatEngageTicks = 0
    /** Track recent damage-per-target for smarter switching */
    private var targetDamageAccum = 0f
    /** When > 0 the bot is retreating from combat to heal */
    private var retreatTicks = 0

    // ─── Threat memory ──────────────────────────────────────────────────
    private data class ThreatMemory(
        var lastSeenTick: Long = 0,
        var totalDamageToOwner: Float = 0f,
        var totalDamageToBot: Float = 0f,
        var attackedOwner: Boolean = false,
        var attackedBot: Boolean = false
    )

    private val threatMemory = mutableMapOf<UUID, ThreatMemory>()
    private var threatCleanupTick = 0

    // ─── Stuck detection ────────────────────────────────────────────────
    private var lastNavPos = Vec3.ZERO
    private var stuckTicks = 0
    private var hardStuckTicks = 0
    private var pathRecalcCooldown = 0

    // ═══════════════════════════════════════════════════════════════════════
    // INVENTORY & CRAFTING
    // ═══════════════════════════════════════════════════════════════════════

    private val blockInventory = mutableMapOf<BlockState, Int>()
    private val maxBlocks = 128

    private var woodLogs = 0
    private var woodPlanks = 0
    private var sticks = 0
    private var cobblestoneCount = 0
    private var dirtCount = 0
    private var coalCount = 0
    private var ironOreCount = 0
    private var ironIngotCount = 0

    private var hasWoodAxe = true
    private var hasStonePickaxe = true
    private var hasIronPickaxe = false
    private var hasStoneSword = false
    private var hasIronSword = true

    // ═══════════════════════════════════════════════════════════════════════
    // BLOCK INTERACTION
    // ═══════════════════════════════════════════════════════════════════════

    private var blockPlaceCooldown = 0
    private var blockBreakCooldown = 0
    private var blockBreakProgress = 0
    private var currentBreakingPos: BlockPos? = null
    private var gatherTargetPos: BlockPos? = null
    private var gatherRetargetCooldown = 0
    private var waterSearchCooldown = 0
    private var avoidLavaCooldown = 0

    // ═══════════════════════════════════════════════════════════════════════
    // PROGRESSION
    // ═══════════════════════════════════════════════════════════════════════

    private var strengthLevel = 1
    private var killCount = 0
    private var damageMitigated = 0.0f

    // ═══════════════════════════════════════════════════════════════════════
    // GOAL REGISTRATION — clean priority hierarchy
    // ═══════════════════════════════════════════════════════════════════════

    override fun registerGoals() {
        //  0 — swim (never drown)
        goalSelector.addGoal(0, FloatGoal(this))
        //  1 — evade un-targetable bosses (Warden, Wither, Dragon)
        goalSelector.addGoal(1, CompanionEvadeDangerGoal(this))
        //  2 — smart melee combat with strafing & crit jumps
        goalSelector.addGoal(2, CompanionSmartCombatGoal(this))
        //  3 — follow owner with prediction + formation
        goalSelector.addGoal(3, CompanionFollowOwnerGoal(this))
        //  4 — break blocks when stuck
        goalSelector.addGoal(4, CompanionBreakObstacleGoal(this))
        //  5 — build up to reach owner at different Y levels
        goalSelector.addGoal(5, CompanionBuildUpGoal(this))
        //  6 — gather resources when idle & safe
        goalSelector.addGoal(6, CompanionGatherGoal(this))
        //  7 — look at nearby player
        goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 10.0f))
        //  8 — idle head movement
        goalSelector.addGoal(8, RandomLookAroundGoal(this))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PATHFINDING — hazard avoidance built into the nav mesh
    // ═══════════════════════════════════════════════════════════════════════

    override fun createNavigation(level: Level): PathNavigation {
        val nav = super.createNavigation(level)
        // Penalise / forbid hazardous nodes so the bot never walks into danger
        setPathfindingMalus(PathType.LAVA, -1.0f)
        setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0f)
        setPathfindingMalus(PathType.DANGER_FIRE, 16.0f)
        setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0f)
        setPathfindingMalus(PathType.DANGER_OTHER, 12.0f)
        setPathfindingMalus(PathType.WATER, 4.0f)
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0f)
        setPathfindingMalus(PathType.DANGER_POWDER_SNOW, 8.0f)
        setPathfindingMalus(PathType.STICKY_HONEY, 12.0f)
        return nav
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SPAWN / INIT
    // ═══════════════════════════════════════════════════════════════════════

    override fun finalizeSpawn(
        accessor: ServerLevelAccessor,
        difficulty: net.minecraft.world.DifficultyInstance,
        reason: EntitySpawnReason,
        spawnData: net.minecraft.world.entity.SpawnGroupData?
    ): net.minecraft.world.entity.SpawnGroupData? {
        val data = super.finalizeSpawn(accessor, difficulty, reason, spawnData)
        setPersistenceRequired()
        seedInventory()
        equipCombatWeapon()
        return data
    }

    private fun seedInventory() {
        blockInventory.clear()
        blockInventory[Blocks.COBBLESTONE.defaultBlockState()] = 48
        blockInventory[Blocks.DIRT.defaultBlockState()] = 32
        blockInventory[Blocks.OAK_PLANKS.defaultBlockState()] = 32
        cobblestoneCount = 48
        dirtCount = 32
        woodPlanks = 32
        sticks = 8
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SYNCHED DATA / OWNER
    // ═══════════════════════════════════════════════════════════════════════

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(OWNER_UUID, "")
    }

    fun setOwner(owner: ServerPlayer) {
        entityData.set(OWNER_UUID, owner.uuid.toString())
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.IRON_SWORD))
        setPersistenceRequired()
        currentState = CompanionState.FOLLOWING
    }

    fun getOwnerUuid(): UUID? {
        val raw = entityData.get(OWNER_UUID)
        if (raw.isBlank()) return null
        return try { UUID.fromString(raw) } catch (_: IllegalArgumentException) { null }
    }

    fun getOwnerPlayer(): ServerPlayer? {
        val uuid = getOwnerUuid() ?: return null
        val serverLevel = level() as? ServerLevel ?: return null
        return serverLevel.server.playerList.getPlayer(uuid)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN AI TICK — lightweight brain; movement is handled by Goals
    // ═══════════════════════════════════════════════════════════════════════

    override fun aiStep() {
        super.aiStep()
        if (level().isClientSide) return
        val serverLevel = level() as? ServerLevel ?: return

        tickCooldowns()
        cleanThreatMemory()
        updateCombatTarget(serverLevel)
        handleEmergencyTeleport()
        handleFallSurvival(serverLevel)
        tickStuckDetection()

        // State inference (goals read this to decide activation)
        // Forced mode from Rhistel overrides normal decision-making
        currentState = when (forcedMode) {
            ForcedMode.ATTACK -> {
                // Force-scan for a target if we don't have one
                if (target == null || !target!!.isAlive) {
                    forceScanForHostile(serverLevel)
                }
                if (target != null) CompanionState.DEFENDING else CompanionState.FOLLOWING
            }
            ForcedMode.FOLLOW -> {
                // Clear any combat target — just stick to owner
                target = null
                CompanionState.FOLLOWING
            }
            ForcedMode.RUN_AWAY -> {
                target = null
                CompanionState.EVADING
            }
            ForcedMode.NONE -> when {
                isEvading() -> CompanionState.EVADING
                target != null && retreatTicks <= 0 -> CompanionState.DEFENDING
                gatherTargetPos != null -> CompanionState.GATHERING
                getOwnerPlayer() != null -> CompanionState.FOLLOWING
                else -> CompanionState.IDLE
            }
        }
    }

    private fun tickCooldowns() {
        if (blockPlaceCooldown > 0) blockPlaceCooldown--
        if (blockBreakCooldown > 0) blockBreakCooldown--
        if (gatherRetargetCooldown > 0) gatherRetargetCooldown--
        if (waterSearchCooldown > 0) waterSearchCooldown--
        if (avoidLavaCooldown > 0) avoidLavaCooldown--
        if (targetRefreshCooldown > 0) targetRefreshCooldown--
        if (ownerDistressTicks > 0) ownerDistressTicks--
        if (retreatTicks > 0) retreatTicks--
        if (pathRecalcCooldown > 0) pathRecalcCooldown--

        // Forced-mode countdown (Rhistel whistle)
        if (forcedModeTicks > 0) {
            forcedModeTicks--
            if (forcedModeTicks <= 0) {
                forcedMode = ForcedMode.NONE
                // Notify owner that forced mode expired
                getOwnerPlayer()?.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§6[Rhistel] §7Forced mode expired — back to §bAUTO§7.")
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // THREAT ASSESSMENT & TARGET SELECTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Scores a living entity's threat level using multiple weighted factors.
     * Higher score = should be targeted first.
     */
    private fun scoreThreat(entity: LivingEntity, owner: ServerPlayer): Double {
        val dist = distanceTo(entity).toDouble().coerceAtLeast(0.5)
        var score = 0.0

        // ── Distance factor (inverse — closer is more urgent) ──────────
        score += (40.0 - dist).coerceAtLeast(0.0) * 1.5

        // ── Targeting owner — TOP priority ─────────────────────────────
        val entityTarget = (entity as? Mob)?.target
        if (entityTarget?.uuid == owner.uuid) score += 120.0
        if (owner.lastHurtByMob?.uuid == entity.uuid) score += 110.0

        // ── Targeting bot ──────────────────────────────────────────────
        if (entityTarget?.uuid == this.uuid) score += 50.0

        // ── Entity type weighting ──────────────────────────────────────
        score += when (entity) {
            is Creeper -> if (dist < 5.0) 150.0 else 70.0   // Creepers close = emergency
            is Skeleton -> 65.0                               // Ranged = dangerous
            is Witch -> 60.0                                  // Debuffs are nasty
            is Spider -> 35.0
            is Monster -> 45.0
            else -> 15.0
        }

        // ── Threat memory bonus ────────────────────────────────────────
        threatMemory[entity.uuid]?.let { mem ->
            if (mem.attackedOwner) score += 40.0
            if (mem.attackedBot) score += 20.0
            score += mem.totalDamageToOwner * 4.0
            score += mem.totalDamageToBot * 2.0
        }

        // ── Low HP — easy finish ───────────────────────────────────────
        if (entity.health < entity.maxHealth * 0.25f) score += 20.0

        // ── Line-of-sight bonus (prefer targets we can reach) ──────────
        if (hasLineOfSight(entity)) score += 15.0

        return score
    }

    private fun updateCombatTarget(serverLevel: ServerLevel) {
        // In FOLLOW or RUN_AWAY forced mode, never acquire targets
        if (forcedMode == ForcedMode.FOLLOW || forcedMode == ForcedMode.RUN_AWAY) {
            target = null
            return
        }

        val owner = getOwnerPlayer() ?: run {
            ownerOfflineTicks++
            if (ownerOfflineTicks > 200) { target = null; retaliationTargetUuid = null }
            return
        }
        ownerOfflineTicks = 0

        // Low HP retreat: disengage and heal for 60 ticks
        if (health < maxHealth * 0.2f && retreatTicks <= 0 && target != null) {
            retreatTicks = 60
            target = null
            retaliationTargetUuid = null
            return
        }
        if (retreatTicks > 0) {
            target = null
            return
        }

        if (targetRefreshCooldown > 0) {
            // Validate existing target
            val cur = target as? LivingEntity
            if (cur != null && (!cur.isAlive || !canTarget(cur) || distanceTo(cur) > 40f)) {
                target = null
                retaliationTargetUuid = null
            }
            return
        }
        targetRefreshCooldown = 8  // reassess every 8 ticks (0.4 s)

        // Scan everything within 28 blocks of the bot AND 20 blocks of the owner
        val botScan = serverLevel.getEntitiesOfClass(
            LivingEntity::class.java, boundingBox.inflate(28.0)
        ) { it != this && it.isAlive && canTarget(it) }

        val ownerScan = serverLevel.getEntitiesOfClass(
            LivingEntity::class.java, owner.boundingBox.inflate(20.0)
        ) { it != this && it.isAlive && canTarget(it) }

        val candidates = (botScan + ownerScan).distinctBy { it.uuid }
        if (candidates.isEmpty()) {
            if (target != null) { target = null; retaliationTargetUuid = null }
            combatEngageTicks = 0
            return
        }

        val best = candidates.maxByOrNull { scoreThreat(it, owner) }
        if (best != null) {
            target = best
            retaliationTargetUuid = best.uuid
            combatEngageTicks++
        }
    }

    private fun cleanThreatMemory() {
        if (++threatCleanupTick < 200) return  // every 10 seconds
        threatCleanupTick = 0
        val now = tickCount.toLong()
        threatMemory.entries.removeIf { now - it.value.lastSeenTick > 600 }
    }

    /**
     * Forced ATTACK mode scan — targets the nearest hostile mob regardless
     * of normal threat scoring.  Called only when [forcedMode] == ATTACK.
     */
    private fun forceScanForHostile(serverLevel: ServerLevel) {
        val hostiles = serverLevel.getEntitiesOfClass(
            Monster::class.java, boundingBox.inflate(30.0)
        ) { it.isAlive && canTarget(it) }
        val nearest = hostiles.minByOrNull { distanceTo(it) }
        if (nearest != null) {
            target = nearest
            retaliationTargetUuid = nearest.uuid
            retreatTicks = 0
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DANGER EVASION (checked by EvadeDangerGoal)
    // ═══════════════════════════════════════════════════════════════════════

    /** Returns the entity we must flee from, or null. */
    fun getDangerEntity(): LivingEntity? {
        val serverLevel = level() as? ServerLevel ?: return null
        val nearby = serverLevel.getEntitiesOfClass(
            LivingEntity::class.java, boundingBox.inflate(24.0)
        ) { it != this && it.isAlive }

        // Priority: Wither > Warden > EnderDragon
        nearby.firstOrNull { it is WitherBoss }?.let { return it }
        nearby.firstOrNull { it is Warden }?.let { return it }
        nearby.firstOrNull { it is EnderDragon && distanceTo(it) < 16f }?.let { return it }
        return null
    }

    private fun isEvading(): Boolean = getDangerEntity() != null || forcedMode == ForcedMode.RUN_AWAY

    // ═══════════════════════════════════════════════════════════════════════
    // STUCK DETECTION & RECOVERY
    // ═══════════════════════════════════════════════════════════════════════

    private fun tickStuckDetection() {
        val pos = position()
        if (pos.distanceToSqr(lastNavPos) < 0.02 && !navigation.isDone) {
            stuckTicks++
            if (stuckTicks > 40) hardStuckTicks++
        } else {
            stuckTicks = 0
            hardStuckTicks = 0
        }
        lastNavPos = pos

        // Hard-stuck for 4+ seconds → emergency: try to break nearby block
        if (hardStuckTicks > 80) {
            forceBreakNearestObstacle()
            hardStuckTicks = 0
        }
    }

    private fun forceBreakNearestObstacle() {
        val ahead = blockPosition().relative(direction)
        val above = ahead.above()
        val level = level()
        if (!level.getBlockState(ahead).isAir) { tryBreakBlock(ahead); return }
        if (!level.getBlockState(above).isAir) { tryBreakBlock(above); return }
        // Try all cardinal directions
        for (dir in Direction.Plane.HORIZONTAL) {
            val pos = blockPosition().relative(dir)
            if (!level.getBlockState(pos).isAir && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0) {
                tryBreakBlock(pos)
                return
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EMERGENCY TELEPORT (only when truly far)
    // ═══════════════════════════════════════════════════════════════════════

    private fun handleEmergencyTeleport() {
        val owner = getOwnerPlayer() ?: return
        val dist = distanceTo(owner)

        if (dist > 48.0) {
            // Owner is far — attempt teleport to safe ground near them
            val tpPos = findSafeTeleportPos(owner)
            if (tpPos != null) {
                teleportTo(tpPos.x + 0.5, tpPos.y.toDouble(), tpPos.z + 0.5)
                navigation.stop()
            } else if (dist > 200.0) {
                // Last resort: raw teleport
                teleportTo(owner.x + 1.0, owner.y, owner.z + 1.0)
                navigation.stop()
            }
        }
    }

    /** Find solid ground near owner that is air above. */
    private fun findSafeTeleportPos(owner: ServerPlayer): BlockPos? {
        val origin = owner.blockPosition()
        val level = level()
        for (r in 1..4) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (abs(dx) != r && abs(dz) != r) continue  // perimeter only
                    for (dy in -2..2) {
                        val pos = origin.offset(dx, dy, dz)
                        val below = pos.below()
                        if (level.getBlockState(below).isSolid &&
                            level.getBlockState(pos).isAir &&
                            level.getBlockState(pos.above()).isAir
                        ) return pos
                    }
                }
            }
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FALL SURVIVAL
    // ═══════════════════════════════════════════════════════════════════════

    private fun handleFallSurvival(serverLevel: ServerLevel) {
        if (onGround() || fallDistance < 3.5f) return

        // Steer into nearby water
        val water = if (waterSearchCooldown <= 0) {
            waterSearchCooldown = 6
            findNearbyWaterLanding(serverLevel, 6, 12)
        } else null

        if (water != null) {
            val target = Vec3(water.x + 0.5, y, water.z + 0.5)
            val dir = target.subtract(position()).normalize()
            deltaMovement = Vec3(dir.x * 0.38, deltaMovement.y, dir.z * 0.38)
            hurtMarked = true
            damageMitigated += 0.4f
            return
        }

        // MLG-style block clutch
        if (fallDistance > 5.0f) {
            val below = blockPosition().below()
            if (level().getBlockState(below).isAir) {
                tryPlaceBlock(below)
                damageMitigated += 0.2f
            }
        }
    }

    private fun findNearbyWaterLanding(serverLevel: ServerLevel, radius: Int, depth: Int): BlockPos? {
        val origin = blockPosition()
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE
        for (dx in -radius..radius) for (dz in -radius..radius) for (dy in 0..depth) {
            val pos = origin.offset(dx, -dy, dz)
            if (!serverLevel.getBlockState(pos).fluidState.`is`(Fluids.WATER)) continue
            val dist = origin.distSqr(pos).toDouble()
            if (dist < bestDist) { bestDist = dist; best = pos }
        }
        return best
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RECALL
    // ═══════════════════════════════════════════════════════════════════════

    fun recallNow() {
        val owner = getOwnerPlayer() ?: return
        val dist = distanceTo(owner)
        if (dist > 48.0) {
            val tp = findSafeTeleportPos(owner)
            if (tp != null) teleportTo(tp.x + 0.5, tp.y.toDouble(), tp.z + 0.5)
            else teleportTo(owner.x + 1.0, owner.y, owner.z + 1.0)
        } else {
            navigation.moveTo(owner, 1.4)
        }
        currentState = CompanionState.FOLLOWING
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BLOCK PLACEMENT / BREAKING
    // ═══════════════════════════════════════════════════════════════════════

    fun tryPlaceBlock(pos: BlockPos): Boolean {
        if (blockPlaceCooldown > 0 || level().isClientSide) return false
        val serverLevel = level() as? ServerLevel ?: return false
        if (!serverLevel.getBlockState(pos).isAir) return false
        val blockToPlace = blockInventory.entries.firstOrNull { it.value > 0 } ?: return false
        val hasSupport = Direction.entries.any { serverLevel.getBlockState(pos.relative(it)).isSolid }
        if (!hasSupport) return false
        serverLevel.setBlock(pos, blockToPlace.key, 3)
        blockInventory[blockToPlace.key] = (blockToPlace.value - 1).coerceAtLeast(0)
        blockPlaceCooldown = 5
        swing(InteractionHand.MAIN_HAND)
        return true
    }

    fun tryBreakBlock(pos: BlockPos): Boolean {
        if (blockBreakCooldown > 0 && currentBreakingPos != pos) return false
        val serverLevel = level() as? ServerLevel ?: return false
        val state = serverLevel.getBlockState(pos)
        if (state.isAir || state.getDestroySpeed(serverLevel, pos) < 0) return false

        if (currentBreakingPos != pos) {
            currentBreakingPos = pos
            blockBreakProgress = 0
        }

        equipToolForBlock(state)
        val hardness = state.getDestroySpeed(serverLevel, pos)
        val base = (hardness * 26).toInt().coerceAtLeast(4)
        val toolMul = when {
            state.`is`(BlockTags.LOGS) && hasWoodAxe -> 0.45
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) && (hasIronPickaxe || hasStonePickaxe) -> 0.5
            else -> 1.6
        }
        val breakTime = (base * toolMul).toInt().coerceAtLeast(4)

        blockBreakProgress++
        swing(InteractionHand.MAIN_HAND)

        if (blockBreakProgress >= breakTime) {
            serverLevel.destroyBlock(pos, false)
            collectResourceFromBrokenBlock(state)
            currentBreakingPos = null
            blockBreakProgress = 0
            blockBreakCooldown = 6
            return true
        }
        return false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESOURCE / CRAFTING
    // ═══════════════════════════════════════════════════════════════════════

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
        while (woodPlanks >= 2 && sticks < 24) { woodPlanks -= 2; sticks += 4 }
    }

    private fun craftProgression() {
        if (!hasStonePickaxe && cobblestoneCount >= 3 && sticks >= 2) {
            cobblestoneCount -= 3; sticks -= 2; hasStonePickaxe = true
        }
        if (!hasStoneSword && cobblestoneCount >= 2 && sticks >= 1) {
            cobblestoneCount -= 2; sticks -= 1; hasStoneSword = true
        }
        if (!hasIronPickaxe && ironIngotCount >= 3 && sticks >= 2) {
            ironIngotCount -= 3; sticks -= 2; hasIronPickaxe = true
        }
        if (!hasIronSword && ironIngotCount >= 2 && sticks >= 1) {
            ironIngotCount -= 2; sticks -= 1; hasIronSword = true
        }
        addBuildingBlock(Blocks.COBBLESTONE.defaultBlockState(), cobblestoneCount.coerceAtMost(6))
        addBuildingBlock(Blocks.DIRT.defaultBlockState(), dirtCount.coerceAtMost(4))
        equipCombatWeapon()
    }

    private fun addBuildingBlock(state: BlockState, count: Int) {
        if (count <= 0) return
        val current = blockInventory[state] ?: 0
        blockInventory[state] = (current + count).coerceAtMost(maxBlocks)
    }

    internal fun equipCombatWeapon() {
        val item = when {
            hasIronSword -> Items.IRON_SWORD
            hasStoneSword -> Items.STONE_SWORD
            else -> Items.WOODEN_SWORD
        }
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack(item))
    }

    private fun equipToolForBlock(state: BlockState) {
        val tool = when {
            state.`is`(BlockTags.LOGS) && hasWoodAxe -> Items.WOODEN_AXE
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) && hasIronPickaxe -> Items.IRON_PICKAXE
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) && hasStonePickaxe -> Items.STONE_PICKAXE
            else -> { equipCombatWeapon(); return }
        }
        if (mainHandItem.item != tool) setItemSlot(EquipmentSlot.MAINHAND, ItemStack(tool))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GATHER UTILITIES (used by CompanionGatherGoal)
    // ═══════════════════════════════════════════════════════════════════════

    fun chooseGatherTarget(level: ServerLevel): BlockPos? {
        if (woodPlanks < 20)
            findNearestBlock(level, 12) { it.`is`(BlockTags.LOGS) }?.let { return it }
        if (cobblestoneCount < 32)
            findNearestBlock(level, 10) {
                it.`is`(Blocks.STONE) || it.`is`(Blocks.COBBLESTONE) || it.`is`(Blocks.DEEPSLATE)
            }?.let { return it }
        if (dirtCount < 24)
            findNearestBlock(level, 10) {
                it.`is`(Blocks.DIRT) || it.`is`(Blocks.GRASS_BLOCK) || it.`is`(Blocks.COARSE_DIRT)
            }?.let { return it }
        return null
    }

    fun findNearestBlock(level: ServerLevel, radius: Int, predicate: (BlockState) -> Boolean): BlockPos? {
        val origin = blockPosition()
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE
        for (dx in -radius..radius) for (dy in -4..4) for (dz in -radius..radius) {
            val pos = origin.offset(dx, dy, dz)
            val state = level.getBlockState(pos)
            if (!predicate(state)) continue
            val dist = origin.distSqr(pos).toDouble()
            if (dist < bestDist) { bestDist = dist; best = pos }
        }
        return best
    }

    fun doGatherTick(serverLevel: ServerLevel) {
        convertLogsToPlanksAndSticks()
        craftProgression()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMBAT CALLBACKS
    // ═══════════════════════════════════════════════════════════════════════

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val attacker = source.entity as? LivingEntity
        if (attacker != null && canTarget(attacker)) {
            retaliationTargetUuid = attacker.uuid
            target = attacker
            // Record threat
            val mem = threatMemory.getOrPut(attacker.uuid) { ThreatMemory() }
            mem.attackedBot = true
            mem.totalDamageToBot += amount
            mem.lastSeenTick = tickCount.toLong()
        }
        // Owner hurt nearby → we remember
        val owner = getOwnerPlayer()
        if (owner != null) {
            val ownerAttacker = owner.lastHurtByMob
            if (ownerAttacker != null && canTarget(ownerAttacker)) {
                val mem = threatMemory.getOrPut(ownerAttacker.uuid) { ThreatMemory() }
                mem.attackedOwner = true
                mem.lastSeenTick = tickCount.toLong()
            }
        }
        ownerDistressTicks = 60
        return super.hurtServer(level, source, amount)
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        // Creeper handling: hit once then back away to avoid explosion
        if (target is Creeper) {
            val hit = super.doHurtTarget(serverLevel, target)
            if (hit) {
                val away = position().subtract(target.position()).normalize().scale(8.0)
                val dest = position().add(away)
                navigation.moveTo(dest.x, dest.y, dest.z, 1.5)
            }
            return hit
        }

        val success = super.doHurtTarget(serverLevel, target)
        if (success) {
            strengthLevel = (strengthLevel + 1).coerceAtMost(200)
            if (target is LivingEntity && target.health <= 0f) killCount++
        }
        return success
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TARGETING VALIDATION
    // ═══════════════════════════════════════════════════════════════════════

    fun canTarget(entity: LivingEntity?): Boolean {
        if (entity == null || !entity.isAlive) return false
        if (entity == this || entity.uuid == getOwnerUuid()) return false
        if (isDangerousEntity(entity)) return false
        // Only target hostile mobs, OR anything that has attacked the owner/bot (retaliation)
        val isHostile = entity is Monster
        val isRetaliation = entity.uuid == retaliationTargetUuid
        val attackedOwner = threatMemory[entity.uuid]?.attackedOwner == true
        val attackedBot = threatMemory[entity.uuid]?.attackedBot == true
        if (!isHostile && !isRetaliation && !attackedOwner && !attackedBot) return false
        return true
    }

    private fun isDangerousEntity(entity: LivingEntity): Boolean =
        entity is Warden || entity is EnderDragon || entity is WitherBoss

    // ═══════════════════════════════════════════════════════════════════════
    // COMBAT HELPERS (used by SmartCombatGoal)
    // ═══════════════════════════════════════════════════════════════════════

    /** Whether incoming arrows are heading towards the bot. */
    fun hasIncomingProjectiles(): Boolean {
        val serverLevel = level() as? ServerLevel ?: return false
        val arrows = serverLevel.getEntitiesOfClass(
            AbstractArrow::class.java, boundingBox.inflate(8.0, 4.0, 8.0)
        )
        return arrows.any { arrow ->
            val traj = arrow.deltaMovement.normalize()
            val toBot = position().subtract(arrow.position()).normalize()
            traj.dot(toBot) > 0.7  // arrow heading roughly toward us
        }
    }

    /** Current strafe direction; periodically toggled. */
    fun getStrafeDir(): Int {
        if (tickCount.toLong() - strafeChangeTick > 30 + random.nextInt(30)) {
            strafeDirection = -strafeDirection
            strafeChangeTick = tickCount.toLong()
        }
        return strafeDirection
    }

    /** Whether the bot should currently be retreating (low HP). */
    fun isRetreating(): Boolean = retreatTicks > 0

    // ═══════════════════════════════════════════════════════════════════════
    // SUMMARY / SNAPSHOT / PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════

    fun getStrengthSummary(): String {
        val atk = "%.1f".format(getAttributeValue(Attributes.ATTACK_DAMAGE))
        val hp = "%.1f".format(maxHealth)
        val speed = "%.2f".format(getAttributeValue(Attributes.MOVEMENT_SPEED))
        val blocks = blockInventory.values.sum()
        return "STR=$strengthLevel DMG=$atk HP=$hp SPD=$speed KILLS=$killCount INV=$blocks MIT=${"%.1f".format(damageMitigated)}"
    }

    fun createSnapshot(): CompanionSnapshot = CompanionSnapshot(
        strengthLevel, killCount, damageMitigated,
        woodLogs, woodPlanks, sticks, cobblestoneCount, dirtCount,
        coalCount, ironOreCount, ironIngotCount,
        hasWoodAxe, hasStonePickaxe, hasIronPickaxe, hasStoneSword, hasIronSword
    )

    fun applySnapshot(snapshot: CompanionSnapshot) {
        strengthLevel = snapshot.strengthLevel.coerceAtLeast(1)
        killCount = snapshot.killCount
        damageMitigated = snapshot.damageMitigated
        woodLogs = snapshot.woodLogs
        woodPlanks = snapshot.woodPlanks
        sticks = snapshot.sticks
        cobblestoneCount = snapshot.cobblestoneCount
        dirtCount = snapshot.dirtCount
        coalCount = snapshot.coalCount
        ironOreCount = snapshot.ironOreCount
        ironIngotCount = snapshot.ironIngotCount
        hasWoodAxe = snapshot.hasWoodAxe
        hasStonePickaxe = snapshot.hasStonePickaxe
        hasIronPickaxe = snapshot.hasIronPickaxe
        hasStoneSword = snapshot.hasStoneSword
        hasIronSword = snapshot.hasIronSword
        craftProgression()
        equipCombatWeapon()
    }

    override fun checkDespawn() { setPersistenceRequired() }
    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false
    override fun requiresCustomPersistence(): Boolean = true

    // ═══════════════════════════════════════════════════════════════════════
    // COMPANION OBJECT
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        private val OWNER_UUID: EntityDataAccessor<String> =
            SynchedEntityData.defineId(CompanionBotEntity::class.java, EntityDataSerializers.STRING)

        data class CompanionSnapshot(
            val strengthLevel: Int,
            val killCount: Int,
            val damageMitigated: Float,
            val woodLogs: Int,
            val woodPlanks: Int,
            val sticks: Int,
            val cobblestoneCount: Int,
            val dirtCount: Int,
            val coalCount: Int,
            val ironOreCount: Int,
            val ironIngotCount: Int,
            val hasWoodAxe: Boolean,
            val hasStonePickaxe: Boolean,
            val hasIronPickaxe: Boolean,
            val hasStoneSword: Boolean,
            val hasIronSword: Boolean
        )

        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 48.0)
            .add(Attributes.MOVEMENT_SPEED, 0.34)
            .add(Attributes.ATTACK_DAMAGE, 4.5)
            .add(Attributes.FOLLOW_RANGE, 80.0)
            .add(Attributes.ARMOR, 6.0)

        fun spawn(level: ServerLevel, pos: BlockPos, owner: ServerPlayer): CompanionBotEntity? {
            val bot = AeonisEntities.COMPANION_BOT.create(level, EntitySpawnReason.COMMAND) ?: return null
            bot.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            bot.setOwner(owner)
            level.addFreshEntity(bot)
            return bot
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: EVADE DANGEROUS ENTITIES (Warden / Wither / Dragon)
// ═══════════════════════════════════════════════════════════════════════════════

private class CompanionEvadeDangerGoal(private val bot: CompanionBotEntity) : Goal() {
    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP) }

    override fun canUse(): Boolean = bot.getDangerEntity() != null

    override fun start() { bot.navigation.stop() }

    override fun tick() {
        val danger = bot.getDangerEntity() ?: return
        val owner = bot.getOwnerPlayer()

        // For Warden: crouch (reduce vibration) and creep away
        bot.setShiftKeyDown(danger is Warden)

        // Flee AWAY from danger but TRY to stay near owner
        val away = bot.position().subtract(danger.position()).normalize()
        var dest = bot.position().add(away.scale(16.0))

        // Bias toward owner if available
        if (owner != null && bot.distanceTo(owner) > 12f) {
            val toOwner = Vec3(owner.x - bot.x, 0.0, owner.z - bot.z).normalize()
            dest = dest.add(toOwner.scale(6.0))
        }

        bot.navigation.moveTo(dest.x, dest.y, dest.z, if (danger is Warden) 1.0 else 1.5)
    }

    override fun stop() { bot.setShiftKeyDown(false) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: SMART MELEE COMBAT
//   • Approaches target intelligently
//   • Strafes in melee range to dodge hits
//   • Jumps for critical hit timing
//   • Retreats from creepers after striking
//   • Evasive movement against ranged mobs (skeletons, etc.)
//   • Retreats to owner when low HP
// ═══════════════════════════════════════════════════════════════════════════════

private class CompanionSmartCombatGoal(private val bot: CompanionBotEntity) : Goal() {
    private var pathRecalc = 0
    private var attackCooldown = 0
    private var approachPhase = true
    private var creeperBackoffTicks = 0

    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK) }

    override fun canUse(): Boolean {
        val t = bot.target as? LivingEntity ?: return false
        return bot.canTarget(t) && !bot.isRetreating() && bot.getDangerEntity() == null
    }

    override fun canContinueToUse(): Boolean {
        val t = bot.target as? LivingEntity ?: return false
        return bot.canTarget(t) && !bot.isRetreating() && bot.getDangerEntity() == null
    }

    override fun start() {
        pathRecalc = 0
        attackCooldown = 0
        approachPhase = true
        creeperBackoffTicks = 0
        bot.equipCombatWeapon()
    }

    override fun tick() {
        val target = bot.target as? LivingEntity ?: return
        val dist = bot.distanceTo(target)

        bot.lookControl.setLookAt(target, 30f, 30f)

        if (attackCooldown > 0) attackCooldown--
        if (creeperBackoffTicks > 0) { creeperBackoffTicks--; return }

        // ── CREEPER SPECIAL: approach → hit → retreat ──────────────────
        if (target is Creeper) {
            handleCreeper(target, dist)
            return
        }

        // ── SKELETON / RANGED SPECIAL: zig-zag approach ────────────────
        val isRanged = target is Skeleton || target is Witch
        if (isRanged && dist > 4.0) {
            zigZagApproach(target, dist)
            return
        }

        // ── GENERAL MELEE ──────────────────────────────────────────────
        if (dist > 3.0) {
            // Approach phase
            if (--pathRecalc <= 0) {
                bot.navigation.moveTo(target, 1.25)
                pathRecalc = 8
            }
            approachPhase = true
        } else {
            // In melee range — strafe and attack
            approachPhase = false
            bot.navigation.stop()

            // Calculate perpendicular strafe vector
            val toTarget = Vec3(target.x - bot.x, 0.0, target.z - bot.z).normalize()
            val perpendicular = Vec3(-toTarget.z, 0.0, toTarget.x) // 90° rotation
            val strafeDir = bot.getStrafeDir()
            val strafeStrength = 0.35
            val maintainDist = toTarget.scale(-0.15) // slight backward pressure to maintain distance
            val strafe = perpendicular.scale(strafeDir * strafeStrength).add(maintainDist)

            bot.moveControl.setWantedPosition(
                bot.x + strafe.x * 3.0,
                bot.y,
                bot.z + strafe.z * 3.0,
                1.0
            )

            // Attack with critical-hit timing (jump → hit while falling)
            if (attackCooldown <= 0 && dist < 2.8) {
                // Attempt critical hit: jump first tick, attack on next
                if (bot.onGround() && bot.random.nextFloat() < 0.45f) {
                    bot.jumpFromGround()
                }
                val serverLevel = bot.level() as? ServerLevel
                if (serverLevel != null) {
                    bot.doHurtTarget(serverLevel, target)
                    attackCooldown = 12
                }
            }
        }

        // Dodge incoming arrows
        if (bot.hasIncomingProjectiles() && bot.onGround()) {
            // Quick lateral dodge
            val toTarget = Vec3(target.x - bot.x, 0.0, target.z - bot.z).normalize()
            val dodge = Vec3(-toTarget.z, 0.4, toTarget.x).scale(if (bot.random.nextBoolean()) 0.6 else -0.6)
            bot.deltaMovement = bot.deltaMovement.add(dodge)
            bot.hurtMarked = true
        }
    }

    private fun handleCreeper(creeper: Creeper, dist: Float) {
        val swellDir = creeper.getSwellDir()
        val isFusing = swellDir > 0

        if (isFusing && dist < 6.0f) {
            // FLEE immediately — creeper is about to explode
            val away = bot.position().subtract(creeper.position()).normalize().scale(12.0)
            val dest = bot.position().add(away)
            bot.navigation.moveTo(dest.x, dest.y, dest.z, 1.6)
            creeperBackoffTicks = 20
            return
        }

        if (dist > 4.5f) {
            // Approach cautiously
            if (--pathRecalc <= 0) {
                bot.navigation.moveTo(creeper, 1.15)
                pathRecalc = 6
            }
        } else if (dist < 3.0f && attackCooldown <= 0) {
            // Strike and immediately retreat
            val serverLevel = bot.level() as? ServerLevel
            if (serverLevel != null) {
                bot.doHurtTarget(serverLevel, creeper)
                attackCooldown = 15
            }
            // Back away after hitting
            val away = bot.position().subtract(creeper.position()).normalize().scale(7.0)
            val dest = bot.position().add(away)
            bot.navigation.moveTo(dest.x, dest.y, dest.z, 1.5)
            creeperBackoffTicks = 15
        } else {
            // Circle at safe distance
            val toTarget = Vec3(creeper.x - bot.x, 0.0, creeper.z - bot.z).normalize()
            val perp = Vec3(-toTarget.z, 0.0, toTarget.x)
            val circle = perp.scale(bot.getStrafeDir() * 0.4)
            bot.moveControl.setWantedPosition(bot.x + circle.x * 4.0, bot.y, bot.z + circle.z * 4.0, 1.0)
        }
    }

    private fun zigZagApproach(target: LivingEntity, dist: Float) {
        // Move toward target but offset laterally every tick to dodge arrows
        val toTarget = Vec3(target.x - bot.x, 0.0, target.z - bot.z).normalize()
        val perpendicular = Vec3(-toTarget.z, 0.0, toTarget.x)
        val zigzagPhase = if ((bot.tickCount / 8) % 2 == 0) 1.0 else -1.0
        val zigzag = toTarget.add(perpendicular.scale(zigzagPhase * 0.7)).normalize()

        val speed = if (dist > 10f) 1.35 else 1.1
        bot.moveControl.setWantedPosition(
            bot.x + zigzag.x * 5.0,
            bot.y,
            bot.z + zigzag.z * 5.0,
            speed
        )

        // Jump periodically to be a harder target
        if (bot.onGround() && bot.tickCount % 15 == 0) {
            bot.jumpFromGround()
        }
    }

    override fun stop() {
        bot.navigation.stop()
        attackCooldown = 0
        creeperBackoffTicks = 0
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: FOLLOW OWNER (predictive, formation-based)
// ═══════════════════════════════════════════════════════════════════════════════

private class CompanionFollowOwnerGoal(private val bot: CompanionBotEntity) : Goal() {
    private var pathRecalc = 0
    private var lastOwnerPos = Vec3.ZERO
    private var ownerVelocity = Vec3.ZERO

    init { flags = EnumSet.of(Flag.MOVE, Flag.LOOK) }

    override fun canUse(): Boolean {
        if (bot.target != null || bot.isRetreating() || bot.getDangerEntity() != null) return false
        val owner = bot.getOwnerPlayer() ?: return false
        return bot.distanceTo(owner) > 4.0
    }

    override fun canContinueToUse(): Boolean {
        if (bot.target != null || bot.isRetreating() || bot.getDangerEntity() != null) return false
        val owner = bot.getOwnerPlayer() ?: return false
        return bot.distanceTo(owner) > 3.0
    }

    override fun tick() {
        val owner = bot.getOwnerPlayer() ?: return
        val ownerPos = owner.position()
        val dist = bot.distanceTo(owner)

        // Track owner velocity for prediction
        ownerVelocity = ownerPos.subtract(lastOwnerPos)
        lastOwnerPos = ownerPos

        bot.lookControl.setLookAt(owner.x, owner.eyeY, owner.z)

        if (--pathRecalc > 0) return

        // Predict where owner will be 15 ticks from now
        val predictedPos = ownerPos.add(ownerVelocity.scale(15.0))

        // Calculate formation position: ~3 blocks behind and 1.5 to the right of owner's facing
        val ownerLook = owner.lookAngle.multiply(1.0, 0.0, 1.0).normalize()
        val perpendicular = Vec3(-ownerLook.z, 0.0, ownerLook.x)
        val formationOffset = ownerLook.scale(-3.0).add(perpendicular.scale(1.5))
        val targetPos = predictedPos.add(formationOffset)

        // Adjust speed based on distance
        val speed = when {
            dist > 24.0 -> 1.5   // Sprint to catch up
            dist > 12.0 -> 1.35  // Jog
            dist > 6.0 -> 1.15   // Walk briskly
            else -> 1.0           // Stroll
        }

        bot.navigation.moveTo(targetPos.x, targetPos.y, targetPos.z, speed)

        // Recalc frequency based on how fast owner is moving
        val ownerSpeed = ownerVelocity.length()
        pathRecalc = when {
            ownerSpeed > 0.2 -> 4   // Owner sprinting — update fast
            ownerSpeed > 0.1 -> 8   // Owner walking
            else -> 15               // Owner still
        }
    }

    override fun stop() {
        bot.navigation.stop()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: BREAK OBSTACLES WHEN STUCK
// ═══════════════════════════════════════════════════════════════════════════════

private class CompanionBreakObstacleGoal(private val bot: CompanionBotEntity) : Goal() {
    private var stuckTicks = 0
    private var lastPos: Vec3? = null

    init { flags = EnumSet.of(Flag.MOVE) }

    override fun canUse(): Boolean {
        if (bot.getDangerEntity() != null) return false
        // Must have somewhere to go
        val hasTarget = bot.target != null || bot.getOwnerPlayer() != null
        if (!hasTarget) return false

        val currentPos = bot.position()
        val last = lastPos
        stuckTicks = if (last != null && currentPos.distanceToSqr(last) < 0.02 && !bot.navigation.isDone) {
            stuckTicks + 1
        } else 0
        lastPos = currentPos

        return stuckTicks > 15 // stuck for 0.75 seconds
    }

    override fun tick() {
        val targetEntity = bot.target ?: bot.getOwnerPlayer() ?: return
        val direction = Vec3(targetEntity.x - bot.x, targetEntity.y - bot.y, targetEntity.z - bot.z).normalize()

        // Break in the direction of our target
        val positions = listOf(
            bot.blockPosition().offset(direction.x.toInt().coerceIn(-1, 1), 0, direction.z.toInt().coerceIn(-1, 1)),
            bot.blockPosition().offset(direction.x.toInt().coerceIn(-1, 1), 1, direction.z.toInt().coerceIn(-1, 1)),
            bot.blockPosition().above(),
            bot.blockPosition().relative(bot.direction),
            bot.blockPosition().relative(bot.direction).above()
        )

        for (pos in positions) {
            val state = bot.level().getBlockState(pos)
            if (!state.isAir && state.getDestroySpeed(bot.level(), pos) >= 0) {
                bot.tryBreakBlock(pos)
                bot.lookControl.setLookAt(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                return
            }
        }
    }

    override fun stop() { stuckTicks = 0 }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: BUILD UP TO REACH OWNER AT DIFFERENT Y LEVEL
// ═══════════════════════════════════════════════════════════════════════════════

private class CompanionBuildUpGoal(private val bot: CompanionBotEntity) : Goal() {
    init { flags = EnumSet.of(Flag.MOVE, Flag.JUMP) }

    override fun canUse(): Boolean {
        if (bot.target != null || bot.getDangerEntity() != null) return false
        val owner = bot.getOwnerPlayer() ?: return false
        return owner.blockPosition().y - bot.blockPosition().y > 3 && bot.distanceTo(owner) < 16f
    }

    override fun canContinueToUse(): Boolean {
        val owner = bot.getOwnerPlayer() ?: return false
        return owner.blockPosition().y - bot.blockPosition().y > 1 && bot.distanceTo(owner) < 20f
    }

    override fun tick() {
        val owner = bot.getOwnerPlayer() ?: return
        if (bot.onGround()) {
            bot.jumpFromGround()
            return
        }
        // Place block under feet while jumping (tower technique)
        if (bot.deltaMovement.y > 0.05) {
            val below = bot.blockPosition().below()
            if (bot.level().getBlockState(below).isAir) {
                bot.tryPlaceBlock(below)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GOAL: GATHER RESOURCES WHEN IDLE AND SAFE
// ═══════════════════════════════════════════════════════════════════════════════

private class CompanionGatherGoal(private val bot: CompanionBotEntity) : Goal() {
    private var gatherPos: BlockPos? = null
    private var retargetCooldown = 0

    init { flags = EnumSet.of(Flag.MOVE) }

    override fun canUse(): Boolean {
        if (bot.target != null || bot.isRetreating() || bot.getDangerEntity() != null) return false
        val owner = bot.getOwnerPlayer() ?: return false
        if (bot.distanceTo(owner) > 10f) return false

        // Only gather when there are no hostiles near owner
        val sl = bot.level() as? ServerLevel ?: return false
        val nearbyMobs = sl.getEntitiesOfClass(Monster::class.java, owner.boundingBox.inflate(16.0)) { it.isAlive }
        return nearbyMobs.isEmpty()
    }

    override fun canContinueToUse(): Boolean {
        if (bot.target != null || bot.isRetreating() || bot.getDangerEntity() != null) return false
        val owner = bot.getOwnerPlayer() ?: return false
        return bot.distanceTo(owner) < 14f
    }

    override fun tick() {
        val sl = bot.level() as? ServerLevel ?: return
        bot.doGatherTick(sl)

        if (--retargetCooldown <= 0 || gatherPos == null) {
            gatherPos = bot.chooseGatherTarget(sl)
            retargetCooldown = 40
        }

        val pos = gatherPos ?: return
        val state = sl.getBlockState(pos)
        if (state.isAir) { gatherPos = null; return }

        val dist = bot.distanceToSqr(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        if (dist > 6.25) {
            bot.navigation.moveTo(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, 1.05)
        } else {
            bot.tryBreakBlock(pos)
        }
    }

    override fun stop() { gatherPos = null }
}
