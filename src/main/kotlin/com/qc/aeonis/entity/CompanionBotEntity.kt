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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import java.util.UUID

class CompanionBotEntity(entityType: EntityType<out CompanionBotEntity>, level: Level) : PathfinderMob(entityType, level) {

    enum class CompanionState {
        IDLE,
        FOLLOWING,
        DEFENDING,
        GATHERING,
        EVADING
    }

    var currentState = CompanionState.IDLE
        private set

    private var retaliationTargetUuid: UUID? = null
    private var ownerOfflineTicks = 0
    private var ownerDistressTicks = 0
    private var targetRefreshCooldown = 0

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

    private var blockPlaceCooldown = 0
    private var blockBreakCooldown = 0
    private var blockBreakProgress = 0
    private var currentBreakingPos: BlockPos? = null
    private var gatherTargetPos: BlockPos? = null
    private var gatherRetargetCooldown = 0
    private var waterSearchCooldown = 0
    private var avoidLavaCooldown = 0

    private var strengthLevel = 1
    private var killCount = 0
    private var damageMitigated = 0.0f

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(0, CompanionMeleeAttackGoal(this, 1.2, true))
        goalSelector.addGoal(1, CompanionFollowOwnerGoal(this))
        goalSelector.addGoal(2, CompanionBreakGoal(this))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 10.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))
    }

    override fun createNavigation(level: Level): PathNavigation = super.createNavigation(level)

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
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun getOwnerPlayer(): ServerPlayer? {
        val uuid = getOwnerUuid() ?: return null
        val serverLevel = level() as? ServerLevel ?: return null
        return serverLevel.server.playerList.getPlayer(uuid)
    }

    override fun aiStep() {
        super.aiStep()
        if (level().isClientSide) return
        val serverLevel = level() as? ServerLevel ?: return

        tickCooldowns()
        updateDangerResponses(serverLevel)
        updateCombatTarget(serverLevel)
        handleFollowAndRecall(serverLevel)
        tickGathering(serverLevel)
        handleFallSurvival(serverLevel)
    }

    private fun tickCooldowns() {
        if (blockPlaceCooldown > 0) blockPlaceCooldown--
        if (blockBreakCooldown > 0) blockBreakCooldown--
        if (gatherRetargetCooldown > 0) gatherRetargetCooldown--
        if (waterSearchCooldown > 0) waterSearchCooldown--
        if (avoidLavaCooldown > 0) avoidLavaCooldown--
        if (targetRefreshCooldown > 0) targetRefreshCooldown--
        if (ownerDistressTicks > 0) ownerDistressTicks--
    }

    private fun updateCombatTarget(serverLevel: ServerLevel) {
        val owner = getOwnerPlayer() ?: return

        val forcedTarget = pickOwnerThreat(owner, serverLevel)
        if (forcedTarget != null) {
            target = forcedTarget
            retaliationTargetUuid = forcedTarget.uuid
            currentState = CompanionState.DEFENDING
            if (distanceTo(forcedTarget) > 2.5) {
                navigation.moveTo(forcedTarget, 1.3)
            }
            return
        }

        val current = target as? LivingEntity
        if (current != null) {
            if (!current.isAlive || !canTarget(current) || distanceTo(current) > 36.0) {
                target = null
                retaliationTargetUuid = null
            } else {
                currentState = CompanionState.DEFENDING
                if (distanceTo(current) > 2.2) {
                    navigation.moveTo(current, 1.25)
                }
                return
            }
        }

        if (targetRefreshCooldown > 0) return
        targetRefreshCooldown = 10

        val hostile = serverLevel.getEntitiesOfClass(
            LivingEntity::class.java,
            owner.boundingBox.inflate(14.0, 8.0, 14.0)
        ) { entity ->
            entity != this &&
                canTarget(entity) &&
                entity is Monster &&
                (entity.target?.uuid == owner.uuid || owner.lastHurtByMob?.uuid == entity.uuid)
        }.minByOrNull { it.distanceToSqr(owner) }

        if (hostile != null) {
            target = hostile
            retaliationTargetUuid = hostile.uuid
            currentState = CompanionState.DEFENDING
        }
    }

    private fun pickOwnerThreat(owner: ServerPlayer, serverLevel: ServerLevel): LivingEntity? {
        val directAttacker = owner.lastHurtByMob
        if (directAttacker != null && canTarget(directAttacker) && distanceTo(directAttacker) < 40.0) {
            return directAttacker
        }

        val recentVictim = owner.lastHurtMob
        if (ownerDistressTicks > 0 && recentVictim is LivingEntity && canTarget(recentVictim) && distanceTo(recentVictim) < 32.0) {
            return recentVictim
        }

        val retaliation = retaliationTargetUuid?.let { uuid ->
            serverLevel.getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(30.0))
                .firstOrNull { it.uuid == uuid && canTarget(it) }
        }
        return retaliation
    }

    private fun updateDangerResponses(serverLevel: ServerLevel) {
        val nearby = serverLevel.getEntitiesOfClass(
            LivingEntity::class.java,
            boundingBox.inflate(22.0)
        ) { it != this && it.isAlive }

        val wither = nearby.firstOrNull { it is WitherBoss }
        if (wither != null) {
            setShiftKeyDown(false)
            currentState = CompanionState.EVADING
            runAwayFrom(wither, 1.55)
            return
        }

        val warden = nearby.firstOrNull { it is Warden }
        if (warden != null) {
            // "Crouch and go" behavior: sneaks and keeps distance while staying near owner.
            setShiftKeyDown(true)
            currentState = CompanionState.EVADING
            runAwayFrom(warden, 1.12)
            return
        }

        setShiftKeyDown(false)
    }

    private fun handleFollowAndRecall(serverLevel: ServerLevel) {
        val owner = getOwnerPlayer()
        if (owner == null) {
            ownerOfflineTicks++
            if (ownerOfflineTicks > 200) {
                currentState = CompanionState.IDLE
                navigation.stop()
            }
            return
        }

        ownerOfflineTicks = 0
        val dist = distanceTo(owner)

        if (dist > 500.0) {
            teleportTo(owner.x + 1.0, owner.y, owner.z + 1.0)
            navigation.stop()
            currentState = CompanionState.FOLLOWING
            return
        }

        currentState = if (target != null) CompanionState.DEFENDING else CompanionState.FOLLOWING

        if (dist > 6.0) {
            navigation.moveTo(owner, 1.25)
        }

        if (owner.blockPosition().y - blockPosition().y > 2) {
            buildTowards(owner.blockPosition())
        }

        avoidLavaPathing(serverLevel)
    }

    private fun runAwayFrom(entity: LivingEntity, speed: Double) {
        val away = position().subtract(entity.position()).normalize().scale(20.0)
        val dest = position().add(away)
        navigation.moveTo(dest.x, dest.y, dest.z, speed)
    }

    fun recallNow() {
        val owner = getOwnerPlayer() ?: return
        val dist = distanceTo(owner)
        if (dist <= 500.0) {
            navigation.moveTo(owner, 1.5)
        } else {
            teleportTo(owner.x + 1.0, owner.y, owner.z + 1.0)
        }
        currentState = CompanionState.FOLLOWING
    }

    private fun avoidLavaPathing(serverLevel: ServerLevel) {
        if (avoidLavaCooldown > 0) return
        avoidLavaCooldown = 6

        val facing = direction
        val ahead = blockPosition().relative(facing)
        val belowAhead = ahead.below()
        val aheadLava = serverLevel.getBlockState(ahead).fluidState.`is`(Fluids.LAVA)
        val belowLava = serverLevel.getBlockState(belowAhead).fluidState.`is`(Fluids.LAVA)

        if (aheadLava || belowLava) {
            val safe = blockPosition().relative(facing.opposite)
            navigation.moveTo(safe.x + 0.5, safe.y.toDouble(), safe.z + 0.5, 1.3)
        }
    }

    private fun tickGathering(serverLevel: ServerLevel) {
        if (target != null) return
        val owner = getOwnerPlayer() ?: return
        if (distanceTo(owner) > 10.0) return
        if (tickCount % 20 != 0) return

        val nearbyHostiles = (level() as? ServerLevel)?.getEntitiesOfClass(
            Monster::class.java,
            owner.boundingBox.inflate(16.0)
        ) { it.isAlive }
        if (!nearbyHostiles.isNullOrEmpty()) return

        convertLogsToPlanksAndSticks()
        craftProgression()

        if (gatherTargetPos == null || gatherRetargetCooldown <= 0 || !isValidGatherTarget(serverLevel, gatherTargetPos!!)) {
            gatherTargetPos = chooseGatherTarget(serverLevel)
            gatherRetargetCooldown = 50
        }

        val gather = gatherTargetPos ?: return
        currentState = CompanionState.GATHERING
        navigation.moveTo(gather.x + 0.5, gather.y.toDouble(), gather.z + 0.5, 1.1)
        if (distanceToSqr(gather.x + 0.5, gather.y.toDouble(), gather.z + 0.5) <= 6.25) {
            tryBreakBlock(gather)
        }
    }

    private fun chooseGatherTarget(level: ServerLevel): BlockPos? {
        if (woodPlanks < 20) {
            findNearestBlock(level, 12) { it.`is`(BlockTags.LOGS) }?.let { return it }
        }
        if (cobblestoneCount < 32) {
            findNearestBlock(level, 10) { it.`is`(Blocks.STONE) || it.`is`(Blocks.COBBLESTONE) || it.`is`(Blocks.DEEPSLATE) }
                ?.let { return it }
        }
        if (dirtCount < 24) {
            findNearestBlock(level, 10) { it.`is`(Blocks.DIRT) || it.`is`(Blocks.GRASS_BLOCK) || it.`is`(Blocks.COARSE_DIRT) }
                ?.let { return it }
        }
        return null
    }

    private fun isValidGatherTarget(level: ServerLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return !state.isAir && state.getDestroySpeed(level, pos) >= 0f
    }

    private fun findNearestBlock(level: ServerLevel, radius: Int, predicate: (BlockState) -> Boolean): BlockPos? {
        val origin = blockPosition()
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (dx in -radius..radius) {
            for (dy in -4..4) {
                for (dz in -radius..radius) {
                    val pos = origin.offset(dx, dy, dz)
                    val state = level.getBlockState(pos)
                    if (!predicate(state)) continue
                    val dist = origin.distSqr(pos).toDouble()
                    if (dist < bestDist) {
                        bestDist = dist
                        best = pos
                    }
                }
            }
        }
        return best
    }

    fun tryPlaceBlock(pos: BlockPos): Boolean {
        if (blockPlaceCooldown > 0 || level().isClientSide) return false
        val serverLevel = level() as? ServerLevel ?: return false
        if (!serverLevel.getBlockState(pos).isAir) return false

        val blockToPlace = blockInventory.entries.firstOrNull { it.value > 0 } ?: return false
        val hasSupport = Direction.entries.any { dir ->
            serverLevel.getBlockState(pos.relative(dir)).isSolid
        }
        if (!hasSupport) return false

        serverLevel.setBlock(pos, blockToPlace.key, 3)
        blockInventory[blockToPlace.key] = (blockToPlace.value - 1).coerceAtLeast(0)
        blockPlaceCooldown = 6
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
        if (blockBreakProgress >= breakTime) {
            serverLevel.destroyBlock(pos, false)
            collectResourceFromBrokenBlock(state)
            currentBreakingPos = null
            blockBreakProgress = 0
            blockBreakCooldown = 8
            return true
        }

        return false
    }

    private fun collectResourceFromBrokenBlock(state: BlockState) {
        when {
            state.`is`(BlockTags.LOGS) -> woodLogs += 1
            state.`is`(Blocks.IRON_ORE) || state.`is`(Blocks.DEEPSLATE_IRON_ORE) -> ironOreCount += 1
            state.`is`(Blocks.COAL_ORE) || state.`is`(Blocks.DEEPSLATE_COAL_ORE) -> coalCount += 1
            state.`is`(Blocks.DIRT) || state.`is`(Blocks.GRASS_BLOCK) || state.`is`(Blocks.COARSE_DIRT) -> {
                dirtCount += 1
                addBuildingBlock(Blocks.DIRT.defaultBlockState(), 1)
            }
            state.`is`(BlockTags.MINEABLE_WITH_PICKAXE) -> {
                cobblestoneCount += 1
                addBuildingBlock(Blocks.COBBLESTONE.defaultBlockState(), 1)
            }
        }
    }

    private fun convertLogsToPlanksAndSticks() {
        if (woodLogs > 0) {
            woodPlanks += woodLogs * 4
            woodLogs = 0
        }
        while (woodPlanks >= 2 && sticks < 24) {
            woodPlanks -= 2
            sticks += 4
        }
    }

    private fun craftProgression() {
        if (!hasStonePickaxe && cobblestoneCount >= 3 && sticks >= 2) {
            cobblestoneCount -= 3
            sticks -= 2
            hasStonePickaxe = true
        }
        if (!hasStoneSword && cobblestoneCount >= 2 && sticks >= 1) {
            cobblestoneCount -= 2
            sticks -= 1
            hasStoneSword = true
        }
        if (!hasIronPickaxe && ironIngotCount >= 3 && sticks >= 2) {
            ironIngotCount -= 3
            sticks -= 2
            hasIronPickaxe = true
        }
        if (!hasIronSword && ironIngotCount >= 2 && sticks >= 1) {
            ironIngotCount -= 2
            sticks -= 1
            hasIronSword = true
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

    private fun equipCombatWeapon() {
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
            else -> {
                equipCombatWeapon()
                return
            }
        }
        if (mainHandItem.item != tool) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack(tool))
        }
    }

    private fun buildTowards(targetPos: BlockPos): Boolean {
        val myPos = blockPosition()
        if (targetPos.y <= myPos.y + 2) return false
        if (onGround()) {
            jumpFromGround()
            return false
        }

        if (deltaMovement.y > 0.05) {
            val placePos = myPos.below()
            if (level().getBlockState(placePos).isAir) {
                return tryPlaceBlock(placePos)
            }
        }
        return false
    }

    private fun handleFallSurvival(serverLevel: ServerLevel) {
        if (onGround() || fallDistance < 3.5f) return

        val water = if (waterSearchCooldown <= 0) {
            waterSearchCooldown = 8
            findNearbyWaterLanding(serverLevel, 6, 12)
        } else null

        if (water != null) {
            val target = Vec3(water.x + 0.5, y, water.z + 0.5)
            val dir = target.subtract(position()).normalize()
            deltaMovement = Vec3(dir.x * 0.35, deltaMovement.y, dir.z * 0.35)
            hurtMarked = true
            damageMitigated += 0.4f
            return
        }

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

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                for (dy in 0..depth) {
                    val pos = origin.offset(dx, -dy, dz)
                    val state = serverLevel.getBlockState(pos)
                    if (!state.fluidState.`is`(Fluids.WATER)) continue
                    val dist = origin.distSqr(pos).toDouble()
                    if (dist < bestDist) {
                        bestDist = dist
                        best = pos
                    }
                }
            }
        }
        return best
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val attacker = source.entity as? LivingEntity
        if (attacker != null && canTarget(attacker)) {
            retaliationTargetUuid = attacker.uuid
            target = attacker
            currentState = CompanionState.DEFENDING
        }
        ownerDistressTicks = 60
        return super.hurtServer(level, source, amount)
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        if (target is Creeper) {
            // Hit once then run 20 blocks away to avoid explosion damage.
            val hit = super.doHurtTarget(serverLevel, target)
            if (hit) {
                val away = position().subtract(target.position()).normalize().scale(20.0)
                val dest = position().add(away)
                navigation.moveTo(dest.x, dest.y, dest.z, 1.5)
            }
            return hit
        }

        val success = super.doHurtTarget(serverLevel, target)
        if (success) {
            strengthLevel = (strengthLevel + 1).coerceAtMost(200)
            if (target is LivingEntity && target.health <= 0f) {
                killCount++
            }
        }
        return success
    }

    fun canTarget(entity: LivingEntity?): Boolean {
        if (entity == null || !entity.isAlive) return false
        if (entity == this || entity.uuid == getOwnerUuid()) return false
        if (isDangerousEntity(entity)) return false
        return true
    }

    private fun isDangerousEntity(entity: LivingEntity): Boolean {
        return entity is Warden || entity is EnderDragon || entity is WitherBoss
    }

    fun getStrengthSummary(): String {
        val atk = "%.1f".format(getAttributeValue(Attributes.ATTACK_DAMAGE))
        val hp = "%.1f".format(maxHealth)
        val speed = "%.2f".format(getAttributeValue(Attributes.MOVEMENT_SPEED))
        val blocks = blockInventory.values.sum()
        return "STR=$strengthLevel DMG=$atk HP=$hp SPD=$speed KILLS=$killCount INV=$blocks MIT=${"%.1f".format(damageMitigated)}"
    }

    fun createSnapshot(): CompanionSnapshot {
        return CompanionSnapshot(
            strengthLevel = strengthLevel,
            killCount = killCount,
            damageMitigated = damageMitigated,
            woodLogs = woodLogs,
            woodPlanks = woodPlanks,
            sticks = sticks,
            cobblestoneCount = cobblestoneCount,
            dirtCount = dirtCount,
            coalCount = coalCount,
            ironOreCount = ironOreCount,
            ironIngotCount = ironIngotCount,
            hasWoodAxe = hasWoodAxe,
            hasStonePickaxe = hasStonePickaxe,
            hasIronPickaxe = hasIronPickaxe,
            hasStoneSword = hasStoneSword,
            hasIronSword = hasIronSword
        )
    }

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

    override fun checkDespawn() {
        setPersistenceRequired()
    }

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    override fun requiresCustomPersistence(): Boolean = true

    companion object {
        private val OWNER_UUID: EntityDataAccessor<String> = SynchedEntityData.defineId(CompanionBotEntity::class.java, EntityDataSerializers.STRING)

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

private class CompanionFollowOwnerGoal(private val bot: CompanionBotEntity) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean = bot.getOwnerPlayer() != null

    override fun tick() {
        val owner = bot.getOwnerPlayer() ?: return
        val dist = bot.distanceTo(owner)
        if (dist > 4.5) {
            bot.navigation.moveTo(owner, 1.2)
        }

        val retaliation = (bot.level() as? ServerLevel)
            ?.server?.playerList
            ?.getPlayer(bot.getOwnerUuid() ?: return)
        if (retaliation != null) {
            bot.lookControl.setLookAt(owner.x, owner.eyeY, owner.z)
        }
    }
}

private class CompanionBreakGoal(private val bot: CompanionBotEntity) : Goal() {
    private var stuckTicks = 0
    private var lastPos: Vec3? = null

    init {
        flags = EnumSet.of(Flag.MOVE)
    }

    override fun canUse(): Boolean {
        val target = bot.target ?: return false
        if (target !is LivingEntity || !bot.canTarget(target)) return false

        val currentPos = bot.position()
        val last = lastPos
        stuckTicks = if (last != null && currentPos.distanceToSqr(last) < 0.01 && !bot.navigation.isDone) {
            stuckTicks + 1
        } else {
            0
        }
        lastPos = currentPos

        return stuckTicks > 12
    }

    override fun tick() {
        val target = bot.target ?: return
        val direction = Vec3(target.x - bot.x, target.y - bot.y, target.z - bot.z).normalize()
        val breakPos = bot.blockPosition().offset(
            direction.x.toInt().coerceIn(-1, 1),
            direction.y.toInt().coerceIn(-1, 1),
            direction.z.toInt().coerceIn(-1, 1)
        )

        if (!bot.level().getBlockState(breakPos).isAir) {
            bot.tryBreakBlock(breakPos)
        }
    }

    override fun stop() {
        stuckTicks = 0
    }
}

private class CompanionMeleeAttackGoal(
    private val bot: CompanionBotEntity,
    speed: Double,
    pauseWhenIdle: Boolean
) : MeleeAttackGoal(bot, speed, pauseWhenIdle) {
    override fun canUse(): Boolean {
        val t = bot.target as? LivingEntity ?: return false
        return bot.canTarget(t) && super.canUse()
    }

    override fun canContinueToUse(): Boolean {
        val t = bot.target as? LivingEntity ?: return false
        return bot.canTarget(t) && super.canContinueToUse()
    }
}
