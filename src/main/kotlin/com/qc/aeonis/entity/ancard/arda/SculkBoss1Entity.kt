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
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:sculkboss1
 */
class SculkBoss1Entity(type: EntityType<out SculkBoss1Entity>, level: Level) :
    PortedArdaGeoMonster(type, level, "sculkboss1", idleAnim = "yasam", walkAnim = "yurume", attackAnim = "saldırınormal") {

    private var attackAnimTicks = 0
    private var roarCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 140.0)
            .add(Attributes.MOVEMENT_SPEED, 0.20)
            .add(Attributes.ATTACK_DAMAGE, 18.0)
            .add(Attributes.ARMOR, 16.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 48.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, SculkBossRoarGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.55))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 20.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(level, target)
        if (did) attackAnimTicks = 16
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (roarCooldown > 0) roarCooldown--
        }
    }

    fun canRoar(): Boolean {
        val t = target ?: return false
        return roarCooldown <= 0 && distanceToSqr(t) <= 10.0 * 10.0
    }

    fun doRoar() {
        roarCooldown = 140
        attackAnimTicks = 20
        val victims = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(7.0))
        for (victim in victims) {
            if (victim === this) continue
            victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 70, 1))
            victim.addEffect(MobEffectInstance(MobEffects.DARKNESS, 60, 0))
            val away = Vec3(victim.x - x, 0.0, victim.z - z).normalize().scale(0.7)
            victim.push(away.x, 0.2, away.z)
        }
        (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(
            ParticleTypes.SONIC_BOOM,
            x,
            eyeY,
            z,
            1,
            0.0,
            0.0,
            0.0,
            0.0
        )
        playSound(SoundEvents.WARDEN_ROAR, 1.2f, 0.65f)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.WARDEN_HEARTBEAT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.IRON_GOLEM_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.WITHER_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SculkBoss1Entity>("move", 5) { test: AnimationTest<SculkBoss1Entity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<SculkBoss1Entity>("attack", 0) { test: AnimationTest<SculkBoss1Entity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}

private class SculkBossRoarGoal(private val boss: SculkBoss1Entity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = boss.canRoar()

    override fun start() {
        boss.doRoar()
    }

    override fun canContinueToUse(): Boolean = false
}
