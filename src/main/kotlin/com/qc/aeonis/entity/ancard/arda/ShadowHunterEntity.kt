package com.qc.aeonis.entity.ancard.arda

import com.qc.aeonis.entity.ancard.geo.LegacyGeoUtil
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
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
import java.util.EnumSet
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:shadowhunter
 */
class ShadowHunterEntity(type: EntityType<out ShadowHunterEntity>, level: Level) :
    PortedArdaGeoMonster(
        type,
        level,
        "shadowhunter",
        idleAnim = "animation.ShadowHunter.ambient",
        walkAnim = "animation.ShadowHunter.walk",
        attackAnim = "animation.ShadowHunter.attack"
    ) {

    private var attackAnimTicks = 0
    private var dashCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.29)
            .add(Attributes.ATTACK_DAMAGE, 9.0)
            .add(Attributes.ARMOR, 6.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.45)
            .add(Attributes.FOLLOW_RANGE, 38.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, ShadowDashGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.15, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.9))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 16.0f))
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
            if (dashCooldown > 0) dashCooldown--
            if (target != null && target!!.isAlive && distanceToSqr(target!!) > 9.0) {
                addEffect(MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false))
            }
        }
    }

    fun canShadowDash(): Boolean {
        val t = target ?: return false
        if (dashCooldown > 0) return false
        val d = distanceTo(t)
        return d in 5.0..14.0 && hasLineOfSight(t)
    }

    fun shadowDash() {
        val t = target ?: return
        val dir = t.position().subtract(position()).normalize()
        setDeltaMovement(dir.x * 1.2, 0.25, dir.z * 1.2)
        addEffect(MobEffectInstance(MobEffects.SPEED, 30, 1, false, false))
        playSound(SoundEvents.PHANTOM_FLAP, 0.9f, 1.2f)
        dashCooldown = 80
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SCULK_CATALYST_STEP
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.PLAYER_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.WITHER_SKELETON_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<ShadowHunterEntity>("move", 4) { test: AnimationTest<ShadowHunterEntity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<ShadowHunterEntity>("attack", 0) { test: AnimationTest<ShadowHunterEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}

private class ShadowDashGoal(private val hunter: ShadowHunterEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = hunter.canShadowDash()

    override fun start() {
        hunter.shadowDash()
    }

    override fun canContinueToUse(): Boolean = false
}
