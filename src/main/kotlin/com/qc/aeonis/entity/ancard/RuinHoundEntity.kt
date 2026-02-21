package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
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
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * ancard:ruin_hound
 * Armored canine with basalt plates.
 *
 * Ability: leap attack and short "stun" (Slowness + brief Blindness).
 */
class RuinHoundEntity(type: EntityType<out RuinHoundEntity>, level: Level) :
    AncardGeoMonster(type, level, "ruin_hound") {

    private var attackAnimTicks = 0
    private var howlCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 26.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.ATTACK_DAMAGE, 6.5)
            .add(Attributes.ARMOR, 7.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
            .add(Attributes.FOLLOW_RANGE, 28.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, LeapAtTargetGoal(this, 0.55f))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.2, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 1.0))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 14.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        val did = super.doHurtTarget(serverLevel, target)
        if (did && target is LivingEntity) {
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 40, 3))
            target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 16, 0))
            playSound(SoundEvents.BASALT_HIT, 1.0f, 0.8f)
            attackAnimTicks = 10
        }
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (howlCooldown > 0) howlCooldown--
            if (target != null && howlCooldown <= 0) {
                howlCooldown = 140
                val pack = level().getEntitiesOfClass(RuinHoundEntity::class.java, boundingBox.inflate(10.0))
                for (hound in pack) {
                    hound.addEffect(MobEffectInstance(MobEffects.SPEED, 80, 0))
                }
                playSound(SoundEvents.RAVAGER_ROAR, 1.0f, 0.8f)
            }
        }
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.RAVAGER_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.RAVAGER_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.RAVAGER_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<RuinHoundEntity>("move", 3) { test: AnimationTest<RuinHoundEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<RuinHoundEntity>("attack", 0) { test: AnimationTest<RuinHoundEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}
