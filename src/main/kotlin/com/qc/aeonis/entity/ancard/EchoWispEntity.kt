package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMob
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * ancard:echo_wisp
 * Tiny glowing spirit, non-hostile until provoked.
 *
 * Ability: emits a disorienting pulse (Darkness + Nausea) when provoked.
 */
class EchoWispEntity(type: EntityType<out EchoWispEntity>, level: Level) :
    AncardGeoMob(type, level, "echo_wisp") {

    private var provokedTicks = 0
    private var pulseCooldown = 0

    init {
        moveControl = FlyingMoveControl(this, 25, true)
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 12.0)
            .add(Attributes.MOVEMENT_SPEED, 0.16)
            .add(Attributes.FLYING_SPEED, 0.42)
            .add(Attributes.FOLLOW_RANGE, 24.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(2, WaterAvoidingRandomFlyingGoal(this, 0.9))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 10.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))
        targetSelector.addGoal(1, HurtByTargetGoal(this))
    }

    override fun createNavigation(level: Level): PathNavigation = FlyingPathNavigation(this, level)

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            setNoGravity(true)
            if (provokedTicks > 0) provokedTicks--
            if (pulseCooldown > 0) pulseCooldown--
            if (provokedTicks <= 0) {
                target = null
            } else if (pulseCooldown <= 0) {
                pulseCooldown = 80
                val players = level().getEntitiesOfClass(Player::class.java, boundingBox.inflate(6.0))
                for (p in players) {
                    p.addEffect(MobEffectInstance(MobEffects.DARKNESS, 60, 0))
                    p.addEffect(MobEffectInstance(MobEffects.NAUSEA, 80, 0))
                }
                (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(
                    ParticleTypes.SONIC_BOOM,
                    x, eyeY, z,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
                )
                playSound(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.9f, 0.6f)
            } else {
                val t = target
                if (t != null && t.isAlive) {
                    val around = t.position().add(
                        kotlin.math.cos((tickCount / 5.0)) * 2.2,
                        1.1,
                        kotlin.math.sin((tickCount / 5.0)) * 2.2
                    )
                    val drift = around.subtract(position()).normalize().scale(0.12)
                    setDeltaMovement(drift)
                }
            }
        }
    }

    override fun hurtServer(serverLevel: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val did = super.hurtServer(serverLevel, source, amount)
        if (did) {
            provokedTicks = 20 * 20
            val attacker = source.entity as? LivingEntity
            if (attacker != null) target = attacker
            playSound(SoundEvents.ALLAY_HURT, 0.8f, 0.7f)
        }
        return did
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.ALLAY_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.ALLAY_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<EchoWispEntity>("move", 3) { test: AnimationTest<EchoWispEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
    }
}
