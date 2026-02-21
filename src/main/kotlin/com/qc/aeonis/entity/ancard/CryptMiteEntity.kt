package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
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
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * ancard:crypt_mite
 * Small swarm creature, insectoid.
 *
 * Ability: when damaged, it calls nearby mites to assist (pack behavior).
 */
class CryptMiteEntity(type: EntityType<out CryptMiteEntity>, level: Level) :
    AncardGeoMonster(type, level, "crypt_mite") {

    private var callCooldown = 0
    private var attackAnimTicks = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.ATTACK_DAMAGE, 2.5)
            .add(Attributes.ARMOR, 1.0)
            .add(Attributes.FOLLOW_RANGE, 20.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, LeapAtTargetGoal(this, 0.4f))
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
            if (callCooldown > 0) callCooldown--
            if (attackAnimTicks > 0) attackAnimTicks--
        }
    }

    override fun hurtServer(serverLevel: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val did = super.hurtServer(serverLevel, source, amount)
        if (did && callCooldown <= 0) {
            callCooldown = 120
            val attacker = source.entity as? LivingEntity
            if (attacker != null) {
                val mites = level().getEntitiesOfClass(CryptMiteEntity::class.java, boundingBox.inflate(10.0))
                for (mite in mites) {
                    if (mite !== this && mite.target == null) {
                        mite.target = attacker
                    }
                }
                playSound(SoundEvents.SILVERFISH_AMBIENT, 0.8f, 1.4f)
            }
        }
        return did
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        val did = super.doHurtTarget(serverLevel, target)
        if (did) {
            attackAnimTicks = 8
            if (target is LivingEntity) {
                target.addEffect(MobEffectInstance(MobEffects.POISON, 50, 0))
            }
        }
        return did
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SILVERFISH_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SILVERFISH_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.SILVERFISH_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<CryptMiteEntity>("move", 3) { test: AnimationTest<CryptMiteEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<CryptMiteEntity>("attack", 0) { test: AnimationTest<CryptMiteEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}
