package com.qc.aeonis.entity.ancard.arda

import com.qc.aeonis.entity.ancard.geo.LegacyGeoUtil
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:sculkcreeperanimation
 */
class SculkCreeperAnimationEntity(type: EntityType<out SculkCreeperAnimationEntity>, level: Level) :
    PortedArdaGeoMonster(type, level, "sculkcreeperanimation", idleAnim = "ambient", walkAnim = "yurume", attackAnim = "bom") {

    private var attackAnimTicks = 0
    private var detonateCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 26.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.FOLLOW_RANGE, 28.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, SculkDetonateGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.2, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.9))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 12.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(level, target)
        if (did) attackAnimTicks = 10
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (detonateCooldown > 0) detonateCooldown--
        }
    }

    fun canDetonatePulse(): Boolean {
        val t = target ?: return false
        return detonateCooldown <= 0 && distanceToSqr(t) <= 4.0 * 4.0
    }

    fun detonatePulse() {
        detonateCooldown = 90
        attackAnimTicks = 14
        val victims = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(3.5))
        for (victim in victims) {
            if (victim === this) continue
            victim.hurt(damageSources().mobAttack(this), 8.0f)
            val dir = Vec3(victim.x - x, 0.0, victim.z - z).normalize().scale(0.7)
            victim.push(dir.x, 0.15, dir.z)
        }
        (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(
            ParticleTypes.SCULK_CHARGE_POP,
            x,
            y + 0.8,
            z,
            25,
            0.8,
            0.5,
            0.8,
            0.03
        )
        playSound(SoundEvents.CREEPER_PRIMED, 0.9f, 1.25f)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SCULK_CATALYST_STEP
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SCULK_BLOCK_HIT
    override fun getDeathSound(): SoundEvent = SoundEvents.SCULK_SHRIEKER_BREAK

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SculkCreeperAnimationEntity>("move", 4) { test: AnimationTest<SculkCreeperAnimationEntity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<SculkCreeperAnimationEntity>("attack", 0) { test: AnimationTest<SculkCreeperAnimationEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}

private class SculkDetonateGoal(private val mob: SculkCreeperAnimationEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = mob.canDetonatePulse()

    override fun start() {
        mob.detonatePulse()
    }

    override fun canContinueToUse(): Boolean = false
}
