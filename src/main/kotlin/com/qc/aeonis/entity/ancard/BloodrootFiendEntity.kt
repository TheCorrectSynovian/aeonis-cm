package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.*
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * Bloodroot Fiend — spawns in the Bloodroot Expanse biome.
 * 
 * Behavior:
 * - Fast melee attacker with high damage
 * - Applies a custom "Bleed" effect on hit (damage over time)
 * - Can leap short distances toward targets
 * - Has a frenzied attack speed that increases at lower health
 */
class BloodrootFiendEntity(type: EntityType<out BloodrootFiendEntity>, level: Level) :
    AncardGeoMonster(type, level, "bloodroot_fiend") {

    private var leapCooldown = 0
    private var attackAnimTicks = 0
    private var leapAnimTicks = 0

    companion object {
        private const val LEAP_COOLDOWN_MAX = 80 // 4 seconds
        private const val BLEED_DURATION = 200 // 10 seconds
        private const val BLEED_AMPLIFIER = 0

        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.35)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.ATTACK_SPEED, 4.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.FOLLOW_RANGE, 40.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, BloodrootLeapGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.2, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 1.0))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 12.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (leapCooldown > 0) leapCooldown--
            if (attackAnimTicks > 0) attackAnimTicks--
            if (leapAnimTicks > 0) leapAnimTicks--

            // Frenzy mode: increase speed when low health
            if (health < maxHealth * 0.3f) {
                if (!hasEffect(MobEffects.SPEED)) {
                    addEffect(MobEffectInstance(MobEffects.SPEED, 40, 1, false, false))
                }
            }
        }
    }

    override fun doHurtTarget(serverLevel: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val hit = super.doHurtTarget(serverLevel, target)
        if (hit && target is LivingEntity) {
            // Placeholder for a future custom BLEED effect.
            target.addEffect(MobEffectInstance(MobEffects.POISON, BLEED_DURATION, BLEED_AMPLIFIER, false, true))
            // Visual/audio feedback
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.5f)
            attackAnimTicks = 10
        }
        return hit
    }

    fun canLeap(): Boolean {
        if (leapCooldown > 0) return false
        val t = target ?: return false
        val dist = distanceTo(t)
        return dist in 4.0..10.0 && onGround()
    }

    fun performLeap() {
        leapCooldown = LEAP_COOLDOWN_MAX
        leapAnimTicks = 15
        val t = target ?: return
        val dir = t.position().subtract(position()).normalize()
        setDeltaMovement(dir.x * 1.2, 0.5, dir.z * 1.2)
        playSound(SoundEvents.RAVAGER_ATTACK, 0.8f, 1.2f)
    }

    override fun fireImmune(): Boolean = false

    override fun getAmbientSound() = SoundEvents.HOGLIN_AMBIENT

    override fun getHurtSound(source: DamageSource) = SoundEvents.HOGLIN_HURT

    override fun getDeathSound() = SoundEvents.HOGLIN_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<BloodrootFiendEntity>("move", 3) { test ->
                val isFrenzied = health < maxHealth * 0.3f
                when {
                    leapAnimTicks > 0 -> test.setAndContinue(AncardGeoUtil.once(animId, "leap"))
                    test.isMoving() && isFrenzied -> test.setAndContinue(AncardGeoUtil.loop(animId, "run"))
                    test.isMoving() -> test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                    else -> test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
                }
            }
        )
        controllers.add(
            AnimationController<BloodrootFiendEntity>("attack", 0) { test ->
                if (attackAnimTicks > 0) test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                else PlayState.STOP
            }
        )
    }
}

/**
 * Leap attack goal for the Bloodroot Fiend.
 */
class BloodrootLeapGoal(private val fiend: BloodrootFiendEntity) : Goal() {
    override fun canUse(): Boolean = fiend.canLeap()

    override fun start() {
        fiend.performLeap()
    }

    override fun canContinueToUse(): Boolean = false
}
