package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState
import java.util.EnumSet

/**
 * ancard:rift_screecher
 * Bat-like (non-vanilla): four wings and torn membranes.
 *
 * Ability: sonic cone attack (knockback + blindness).
 */
class RiftScreecherEntity(type: EntityType<out RiftScreecherEntity>, level: Level) :
    AncardGeoMonster(type, level, "rift_screecher") {

    private var sonicCooldown = 0
    private var attackAnimTicks = 0

    init {
        moveControl = FlyingMoveControl(this, 25, true)
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 22.0)
            .add(Attributes.MOVEMENT_SPEED, 0.15)
            .add(Attributes.FLYING_SPEED, 0.55)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.FOLLOW_RANGE, 40.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(2, SonicConeGoal(this))
        goalSelector.addGoal(5, WaterAvoidingRandomFlyingGoal(this, 1.0))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 18.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun createNavigation(level: Level): PathNavigation = FlyingPathNavigation(this, level)

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            setNoGravity(true)
            if (sonicCooldown > 0) sonicCooldown--
            if (attackAnimTicks > 0) attackAnimTicks--
        }
    }

    fun canSonic(): Boolean {
        val t = target ?: return false
        return sonicCooldown <= 0 && hasLineOfSight(t) && distanceToSqr(t) <= 12.0 * 12.0
    }

    fun doSonic() {
        val t = target ?: return
        sonicCooldown = 90
        attackAnimTicks = 18

        val origin = eyePosition
        val forward = t.eyePosition.subtract(origin).normalize()
        val cosHalfAngle = kotlin.math.cos(Math.toRadians(35.0))

        val victims = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(12.0))
        for (v in victims) {
            if (v === this) continue
            val to = v.eyePosition.subtract(origin)
            val dist = to.length()
            if (dist < 0.001 || dist > 12.0) continue
            val dir = to.normalize()
            val dot = dir.dot(forward)
            if (dot < cosHalfAngle) continue

            // Knockback scales with closeness and alignment.
            val strength = (0.9 * (1.0 - (dist / 12.0)) * dot).coerceAtLeast(0.15)
            val push = dir.scale(strength)
            v.push(push.x, 0.05, push.z)
            v.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 40, 0))
        }

        (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(
            ParticleTypes.SONIC_BOOM,
            x, eyeY, z,
            1,
            0.0, 0.0, 0.0,
            0.0
        )
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.35f)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.PHANTOM_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.PHANTOM_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.PHANTOM_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<RiftScreecherEntity>("move", 3) { test: AnimationTest<RiftScreecherEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<RiftScreecherEntity>("attack", 0) { test: AnimationTest<RiftScreecherEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}

private class SonicConeGoal(private val screecher: RiftScreecherEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.LOOK))
    }

    override fun canUse(): Boolean = screecher.canSonic()

    override fun start() {
        screecher.doSonic()
    }

    override fun canContinueToUse(): Boolean = false
}
