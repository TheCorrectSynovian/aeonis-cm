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
import net.minecraft.world.entity.AreaEffectCloud
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * ancard:sporeback
 * Large carrier creature with glowing fungal sacks.
 *
 * Ability: on being hit, releases a spore cloud (area effect).
 */
class SporebackEntity(type: EntityType<out SporebackEntity>, level: Level) :
    AncardGeoMob(type, level, "sporeback") {

    private var sporeCooldown = 0
    private var attackAnimTicks = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 48.0)
            .add(Attributes.MOVEMENT_SPEED, 0.16)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.ARMOR, 8.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
            .add(Attributes.FOLLOW_RANGE, 20.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(2, MeleeAttackGoal(this, 0.9, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.65))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 10.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        // Neutral-ish: only retaliates when attacked.
        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, 10, true, false) { entity, _ ->
            distanceToSqr(entity) <= 8.0 * 8.0
        })
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (sporeCooldown > 0) sporeCooldown--
            if (attackAnimTicks > 0) attackAnimTicks--
        }
    }

    override fun hurtServer(serverLevel: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val did = super.hurtServer(serverLevel, source, amount)
        if (did && sporeCooldown <= 0) {
            sporeCooldown = 140
            releaseSpores()
        }
        return did
    }

    private fun releaseSpores() {
        val cloud = AreaEffectCloud(level(), x, y + 0.2, z)
        cloud.setRadius(3.5f)
        cloud.setDuration(60)
        cloud.setRadiusOnUse(-0.2f)
        cloud.setRadiusPerTick(-0.02f)
        cloud.setCustomParticle(ParticleTypes.SPORE_BLOSSOM_AIR)
        cloud.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 80, 1))
        cloud.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 80, 0))
        cloud.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 30, 0))
        level().addFreshEntity(cloud)
        playSound(SoundEvents.SPORE_BLOSSOM_STEP, 1.0f, 0.7f)
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(serverLevel, target)
        if (did) attackAnimTicks = 12
        return did
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SNIFFER_IDLE
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SNIFFER_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.SNIFFER_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SporebackEntity>("move", 3) { test: AnimationTest<SporebackEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<SporebackEntity>("attack", 0) { test: AnimationTest<SporebackEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}
