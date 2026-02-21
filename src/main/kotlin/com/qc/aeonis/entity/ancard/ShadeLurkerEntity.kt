package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
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
 * ancard:shade_lurker
 * Quadruped shadow-beast with long forelimbs.
 *
 * Ability: on being hit, it "phases" briefly (invulnerable window).
 */
class ShadeLurkerEntity(type: EntityType<out ShadeLurkerEntity>, level: Level) :
    AncardGeoMonster(type, level, "shade_lurker") {

    private var phasingTicks = 0
    private var phaseCooldownTicks = 0
    private var attackAnimTicks = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 34.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.35)
            .add(Attributes.FOLLOW_RANGE, 36.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.1, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.9))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 16.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (phasingTicks > 0) {
                phasingTicks--
                // Visual stealth cue.
                if (!isInvisible) isInvisible = true
                if (phasingTicks == 0) isInvisible = false
            }
            if (phaseCooldownTicks > 0) phaseCooldownTicks--
            if (attackAnimTicks > 0) attackAnimTicks--
        }
    }

    override fun hurtServer(serverLevel: ServerLevel, source: DamageSource, amount: Float): Boolean {
        if (phasingTicks > 0) return false
        val did = super.hurtServer(serverLevel, source, amount)
        if (did && phaseCooldownTicks <= 0) {
            phasingTicks = 18
            phaseCooldownTicks = 80
            playSound(SoundEvents.ENDERMAN_TELEPORT, 0.7f, 0.55f)
        }
        return did
    }

    override fun isInvulnerableTo(serverLevel: ServerLevel, damageSource: DamageSource): Boolean {
        if (phasingTicks > 0) return true
        return super.isInvulnerableTo(serverLevel, damageSource)
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(serverLevel, target)
        if (did) attackAnimTicks = 10
        return did
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SCULK_CATALYST_HIT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SCULK_BLOCK_HIT
    override fun getDeathSound(): SoundEvent = SoundEvents.SCULK_SHRIEKER_BREAK

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<ShadeLurkerEntity>("move", 3) { test: AnimationTest<ShadeLurkerEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<ShadeLurkerEntity>("attack", 0) { test: AnimationTest<ShadeLurkerEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}
