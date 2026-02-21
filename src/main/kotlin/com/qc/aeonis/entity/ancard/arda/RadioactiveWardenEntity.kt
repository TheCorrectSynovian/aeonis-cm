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
 * Ported from Arda's Sculks (GeckoLib assets already present).
 * Entity id: aeonis:radioactivewarden
 */
class RadioactiveWardenEntity(type: EntityType<out RadioactiveWardenEntity>, level: Level) :
    PortedArdaGeoMonster(type, level, "radioactivewarden", idleAnim = "ambient", walkAnim = "walk", attackAnim = "attack") {

    private var attackAnimTicks = 0
    private var pulseCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 70.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(Attributes.ATTACK_DAMAGE, 12.0)
            .add(Attributes.ARMOR, 10.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
            .add(Attributes.FOLLOW_RANGE, 40.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, RadiationPulseGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.7))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 16.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(level, target)
        if (did) attackAnimTicks = 14
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (pulseCooldown > 0) pulseCooldown--
        }
    }

    fun canPulse(): Boolean {
        val t = target ?: return false
        return pulseCooldown <= 0 && distanceToSqr(t) <= 9.0 * 9.0
    }

    fun pulseRadiation() {
        pulseCooldown = 120
        attackAnimTicks = 18

        val victims = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(6.0))
        for (victim in victims) {
            if (victim === this) continue
            victim.addEffect(MobEffectInstance(MobEffects.POISON, 80, 1))
            victim.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 80, 0))
            victim.addEffect(MobEffectInstance(MobEffects.GLOWING, 60, 0))
        }
        (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(
            ParticleTypes.HAPPY_VILLAGER,
            x,
            y + 1.2,
            z,
            20,
            1.2,
            0.5,
            1.2,
            0.02
        )
        playSound(SoundEvents.WARDEN_ROAR, 1.0f, 0.6f)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.WARDEN_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.WARDEN_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.WARDEN_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<RadioactiveWardenEntity>("move", 4) { test: AnimationTest<RadioactiveWardenEntity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<RadioactiveWardenEntity>("attack", 0) { test: AnimationTest<RadioactiveWardenEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}

private class RadiationPulseGoal(private val mob: RadioactiveWardenEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = mob.canPulse()

    override fun start() {
        mob.pulseRadiation()
    }

    override fun canContinueToUse(): Boolean = false
}
