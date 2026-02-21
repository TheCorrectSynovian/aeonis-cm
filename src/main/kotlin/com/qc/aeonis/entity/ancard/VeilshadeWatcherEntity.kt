package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.*
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * Veilshade Watcher — spawns in the Veilshade Hollow biome.
 * 
 * Behavior:
 * - Low aggression: only attacks if player attacks first
 * - Teleports frequently (like Endermen)
 * - When aggressive, teleports behind player and strikes
 * - Emits glowing particles
 * - Relatively low damage but very hard to hit
 */
class VeilshadeWatcherEntity(type: EntityType<out VeilshadeWatcherEntity>, level: Level) :
    AncardGeoMonster(type, level, "veilshade_watcher") {

    private var teleportCooldown = 0
    private var watchTicks = 0
    private var isAggressive = false
    private var teleportAnimTicks = 0
    private var attackAnimTicks = 0

    companion object {
        private const val TELEPORT_COOLDOWN_MAX = 60 // 3 seconds
        private const val WATCH_RANGE = 24.0
        private const val AGGRO_FORGET_TIME = 600 // 30 seconds

        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 25.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.ARMOR, 0.0)
            .add(Attributes.FOLLOW_RANGE, 48.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, VeilshadeWatcherTeleportGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, false))
        goalSelector.addGoal(4, WaterAvoidingRandomStrollGoal(this, 0.6))
        goalSelector.addGoal(5, LookAtPlayerGoal(this, Player::class.java, WATCH_RANGE.toFloat()))
        goalSelector.addGoal(6, RandomLookAroundGoal(this))

        // Only retaliates — does NOT auto-target players
        targetSelector.addGoal(1, HurtByTargetGoal(this))
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (teleportCooldown > 0) teleportCooldown--
            if (teleportAnimTicks > 0) teleportAnimTicks--
            if (attackAnimTicks > 0) attackAnimTicks--

            // Track aggression timeout
            if (isAggressive) {
                watchTicks++
                if (watchTicks > AGGRO_FORGET_TIME || target == null || !target!!.isAlive) {
                    isAggressive = false
                    watchTicks = 0
                    target = null
                }
            }

            // Defensive teleport: if damaged while not aggressive, teleport away
            // (handled in hurt)
        }
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val result = super.hurtServer(level, source, amount)
        if (result) {
            isAggressive = true
            watchTicks = 0

            // Reactive teleport behind attacker
            val attacker = source.entity
            if (attacker != null && teleportCooldown <= 0) {
                teleportBehind(attacker.position(), attacker.lookAngle)
                teleportCooldown = TELEPORT_COOLDOWN_MAX / 2 // Faster reactive teleport
                teleportAnimTicks = 12
                attackAnimTicks = 10
            }
        }
        return result
    }

    fun canTeleport(): Boolean = teleportCooldown <= 0 && isAggressive && target != null

    fun teleportNearTarget() {
        val t = target ?: return
        teleportBehind(t.position(), t.lookAngle)
        teleportCooldown = TELEPORT_COOLDOWN_MAX
        teleportAnimTicks = 12
    }

    private fun teleportBehind(targetPos: Vec3, targetLook: Vec3) {
        val behind = targetPos.subtract(targetLook.normalize().scale(3.0))
        val safeY = findSafeY(behind)
        teleportTo(behind.x, safeY, behind.z)
        playSound(SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.5f)
        // Brief speed boost after teleport
        addEffect(MobEffectInstance(MobEffects.SPEED, 20, 1, false, false))
    }

    private fun findSafeY(pos: Vec3): Double {
        val blockPos = BlockPos.containing(pos)
        for (y in blockPos.y downTo blockPos.y - 5) {
            val below = BlockPos(blockPos.x, y - 1, blockPos.z)
            if (!level().getBlockState(below).isAir) {
                return y.toDouble()
            }
        }
        return pos.y
    }

    override fun getAmbientSound() = SoundEvents.ENDERMAN_AMBIENT

    override fun getHurtSound(source: DamageSource) = SoundEvents.ENDERMAN_HURT

    override fun getDeathSound() = SoundEvents.ENDERMAN_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<VeilshadeWatcherEntity>("move", 3) { test ->
                when {
                    teleportAnimTicks > 0 -> test.setAndContinue(AncardGeoUtil.once(animId, "teleport"))
                    test.isMoving() -> test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                    isAggressive -> test.setAndContinue(AncardGeoUtil.loop(animId, "stare"))
                    else -> test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
                }
            }
        )
        controllers.add(
            AnimationController<VeilshadeWatcherEntity>("attack", 0) { test ->
                if (attackAnimTicks > 0) test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                else PlayState.STOP
            }
        )
    }
}

/**
 * Teleport goal for the Veilshade Watcher.
 */
class VeilshadeWatcherTeleportGoal(private val watcher: VeilshadeWatcherEntity) : Goal() {
    override fun canUse(): Boolean = watcher.canTeleport()

    override fun start() {
        watcher.teleportNearTarget()
    }

    override fun canContinueToUse(): Boolean = false
}
