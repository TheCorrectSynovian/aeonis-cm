package com.qc.aeonis.entity.ancard

import com.qc.aeonis.effect.AncardEffects
import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
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
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.util.EnumSet
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * ancard:boneweaver
 * Spider-like but not vanilla spider: tall, thin, rib-cage frame.
 *
 * Ability: applies "fracture" debuff on hit.
 */
class BoneweaverEntity(type: EntityType<out BoneweaverEntity>, level: Level) :
    AncardGeoMonster(type, level, "boneweaver") {

    private var attackAnimTicks = 0
    private var snareCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 28.0)
            .add(Attributes.MOVEMENT_SPEED, 0.27)
            .add(Attributes.ATTACK_DAMAGE, 6.0)
            .add(Attributes.ARMOR, 3.0)
            .add(Attributes.FOLLOW_RANGE, 34.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, BoneweaverSnareGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.05, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.85))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 18.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        val did = super.doHurtTarget(serverLevel, target)
        if (did && target is net.minecraft.world.entity.LivingEntity) {
            val fracture = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(AncardEffects.FRACTURE)
            target.addEffect(MobEffectInstance(fracture, 160, 0))
            attackAnimTicks = 10
        }
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (snareCooldown > 0) snareCooldown--
        }
    }

    fun canCastSnare(): Boolean {
        val t = target ?: return false
        val d = distanceTo(t)
        return snareCooldown <= 0 && d in 5.0..14.0
    }

    fun castSnare() {
        val t = target as? LivingEntity ?: return
        t.addEffect(MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 2))
        t.addEffect(MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 60, 0))
        (level() as? ServerLevel)?.sendParticles(
            ParticleTypes.WHITE_SMOKE,
            t.x, t.y + 1.0, t.z,
            10, 0.3, 0.4, 0.3, 0.02
        )
        playSound(SoundEvents.SKELETON_SHOOT, 0.8f, 0.7f)
        snareCooldown = 90
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SKELETON_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SKELETON_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.SKELETON_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<BoneweaverEntity>("move", 3) { test: AnimationTest<BoneweaverEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<BoneweaverEntity>("attack", 0) { test: AnimationTest<BoneweaverEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}

private class BoneweaverSnareGoal(private val boneweaver: BoneweaverEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.LOOK))
    }

    override fun canUse(): Boolean = boneweaver.canCastSnare()

    override fun start() {
        boneweaver.castSnare()
    }

    override fun canContinueToUse(): Boolean = false
}
