package com.qc.aeonis.entity.ancard.arda

import com.qc.aeonis.entity.ancard.geo.LegacyGeoUtil
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal
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
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:sculkslime
 */
class SculkSlimeEntity(type: EntityType<out SculkSlimeEntity>, level: Level) :
    PortedArdaGeoMonster(type, level, "sculkslime", idleAnim = "ambient", walkAnim = "walk", attackAnim = "attack") {

    private var attackAnimTicks = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.FOLLOW_RANGE, 24.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, LeapAtTargetGoal(this, 0.5f))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.6))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 12.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(level, target)
        if (did) {
            attackAnimTicks = 10
            if (target is LivingEntity) {
                target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 50, 1))
                target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 0))
            }
        }
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide && attackAnimTicks > 0) attackAnimTicks--
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SLIME_SQUISH
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SLIME_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.SLIME_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SculkSlimeEntity>("move", 4) { test: AnimationTest<SculkSlimeEntity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<SculkSlimeEntity>("attack", 0) { test: AnimationTest<SculkSlimeEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}
