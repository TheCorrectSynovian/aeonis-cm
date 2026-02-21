package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
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
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState
import java.util.EnumSet

/**
 * ancard:ancient_colossus
 * Mini-boss tier golem of obsidian shale + glowing runes.
 *
 * Ability: slow, devastating slam (AOE) with "ground crack" VFX (no block breaking).
 */
class AncientColossusEntity(type: EntityType<out AncientColossusEntity>, level: Level) :
    AncardGeoMonster(type, level, "ancient_colossus") {

    private var slamCooldown = 0
    private var slamWindup = 0
    private var slamAnimTicks = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 220.0)
            .add(Attributes.MOVEMENT_SPEED, 0.16)
            .add(Attributes.ATTACK_DAMAGE, 14.0)
            .add(Attributes.ARMOR, 18.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 48.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, ColossusSlamGoal(this))
        goalSelector.addGoal(3, MeleeAttackGoal(this, 0.9, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.6))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 22.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (slamCooldown > 0) slamCooldown--
            if (slamAnimTicks > 0) slamAnimTicks--
            if (slamWindup > 0) {
                slamWindup--
                navigation.stop()
                deltaMovement = Vec3.ZERO
                if (slamWindup == 0) doSlam()
            }
        }
    }

    fun canStartSlam(): Boolean {
        val t = target ?: return false
        if (!t.isAlive) return false
        if (slamCooldown > 0 || slamWindup > 0) return false
        if (!hasLineOfSight(t)) return false
        return distanceToSqr(t) <= 5.0 * 5.0
    }

    fun startSlam() {
        slamWindup = 18
        slamAnimTicks = 26
        playSound(SoundEvents.ANVIL_PLACE, 1.0f, 0.65f)
    }

    private fun doSlam() {
        slamCooldown = 140
        val radius = 4.5
        val victims = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(radius))
        for (v in victims) {
            if (v === this) continue
            if (distanceToSqr(v) > radius * radius) continue
            val src = damageSources().mobAttack(this)
            v.hurt(src, 14.0f)

            val away = Vec3(v.x - x, 0.0, v.z - z)
            val push = if (away.lengthSqr() < 1.0E-6) Vec3(0.0, 0.0, 0.0) else away.normalize().scale(0.9)
            v.push(push.x, 0.35, push.z)
        }

        (level() as? net.minecraft.server.level.ServerLevel)?.let { server ->
            server.sendParticles(
                ParticleTypes.ASH,
                x, y + 0.1, z,
                30,
                1.2, 0.05, 1.2,
                0.2
            )
            server.sendParticles(
                ParticleTypes.SONIC_BOOM,
                x, eyeY, z,
                1,
                0.0, 0.0, 0.0,
                0.0
            )
            server.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                x, y + 0.2, z,
                12,
                0.8, 0.1, 0.8,
                0.02
            )
        }
        playSound(SoundEvents.ANVIL_LAND, 0.9f, 0.6f)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.IRON_GOLEM_REPAIR
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.IRON_GOLEM_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.IRON_GOLEM_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<AncientColossusEntity>("move", 4) { test: AnimationTest<AncientColossusEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<AncientColossusEntity>("attack", 0) { test: AnimationTest<AncientColossusEntity> ->
                if (slamAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "slam"))
                }
                PlayState.STOP
            }
        )
    }
}

private class ColossusSlamGoal(private val colossus: AncientColossusEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = colossus.canStartSlam()

    override fun start() {
        colossus.startSlam()
    }

    override fun canContinueToUse(): Boolean = false
}
