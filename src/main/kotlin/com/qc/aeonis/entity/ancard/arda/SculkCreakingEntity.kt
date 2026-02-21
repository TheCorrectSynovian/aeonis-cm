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
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.EnumSet
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:sculkcreaking
 */
class SculkCreakingEntity(type: EntityType<out SculkCreakingEntity>, level: Level) :
    PortedArdaGeoMonster(type, level, "sculkcreaking", idleAnim = "ambient", walkAnim = "yurume", attackAnim = "attack") {

    private var attackAnimTicks = 0
    private var screechCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 32.0)
            .add(Attributes.MOVEMENT_SPEED, 0.27)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
            .add(Attributes.FOLLOW_RANGE, 32.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, SculkCreakingScreechGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.15, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.8))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 14.0f))
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
            if (screechCooldown > 0) screechCooldown--
        }
    }

    fun canScreech(): Boolean {
        val t = target ?: return false
        return screechCooldown <= 0 && distanceToSqr(t) <= 8.0 * 8.0
    }

    fun doScreech() {
        screechCooldown = 120
        attackAnimTicks = 14
        val victims = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(5.5))
        for (victim in victims) {
            if (victim === this) continue
            victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 70, 1))
            victim.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 70, 0))
        }
        (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(
            ParticleTypes.SCULK_SOUL,
            x,
            y + 1.0,
            z,
            20,
            1.0,
            0.7,
            1.0,
            0.02
        )
        playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0f, 0.8f)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SCULK_SHRIEKER_STEP
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SCULK_BLOCK_HIT
    override fun getDeathSound(): SoundEvent = SoundEvents.SCULK_SHRIEKER_BREAK

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SculkCreakingEntity>("move", 4) { test: AnimationTest<SculkCreakingEntity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<SculkCreakingEntity>("attack", 0) { test: AnimationTest<SculkCreakingEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}

private class SculkCreakingScreechGoal(private val mob: SculkCreakingEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.LOOK))
    }

    override fun canUse(): Boolean = mob.canScreech()

    override fun start() {
        mob.doScreech()
    }

    override fun canContinueToUse(): Boolean = false
}
